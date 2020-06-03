package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.AsPropertyTypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class NestedPropertyTypeResolver extends StdTypeResolverBuilder {

    @Override
    public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
        if (_idType == JsonTypeInfo.Id.NONE || baseType.isPrimitive() || _includeAs != JsonTypeInfo.As.PROPERTY) {
            return super.buildTypeDeserializer(config, baseType, subtypes);
        }

        PolymorphicTypeValidator subTypeValidator = verifyBaseTypeValidity(config, baseType);
        TypeIdResolver idResolver = idResolver(config, baseType, subTypeValidator, subtypes, false, true);
        JavaType defaultImpl = defineDefaultImpl(config, baseType);
        return new AsNestedPropertyDeserializer(baseType, idResolver, _typeProperty, _typeIdVisible, defaultImpl);
    }

    private static class AsNestedPropertyDeserializer extends AsPropertyTypeDeserializer {


        public AsNestedPropertyDeserializer(JavaType baseType, TypeIdResolver idResolver, String propertyName, boolean isVisible, JavaType defaultImpl) {
            super(baseType, idResolver, propertyName, isVisible, defaultImpl);
        }

        public AsNestedPropertyDeserializer(AsPropertyTypeDeserializer source, final BeanProperty property) {
            super(source, property);
        }

        @Override
        public TypeDeserializer forProperty(BeanProperty property) {
            return (property == _property) ? this : new AsNestedPropertyDeserializer(this, property);
        }

        @Override
        public Object deserializeTypedFromObject(JsonParser parser, DeserializationContext context) throws IOException {
            List<String> path = Arrays.stream(_typePropertyName.split("\\."))
                    .collect(Collectors.toList());
            return findDeserializer(parser, path, context, new TokenBuffer(parser, context));
        }

        private Object findDeserializer(JsonParser parser, List<String> path, DeserializationContext context, TokenBuffer tokenBuffer) throws IOException {
            if (path.isEmpty()) {
                return _deserializeTypedUsingDefaultImpl(parser, context, tokenBuffer);
            }

            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                token = parser.nextToken();
                while (token == JsonToken.FIELD_NAME) {
                    String fieldName = parser.currentName();
                    parser.nextToken();
                    if (fieldName.equals(path.get(0))) {
                        List<String> subPath = path.subList(1, path.size());
                        if (subPath.isEmpty()) {
                            return _deserializeTypedForId(parser, context, tokenBuffer);
                        } else if (parser.currentToken() == JsonToken.START_OBJECT) {
                            tokenBuffer.writeFieldName(fieldName);
                            tokenBuffer.writeStartObject();
                            return findDeserializer(parser, subPath, context, tokenBuffer);
                        }
                    }
                    tokenBuffer.writeFieldName(fieldName);
                    tokenBuffer.copyCurrentStructure(parser);
                    token = parser.nextToken();
                }
            }

            return _deserializeTypedUsingDefaultImpl(parser, context, tokenBuffer);
        }

    }
}
