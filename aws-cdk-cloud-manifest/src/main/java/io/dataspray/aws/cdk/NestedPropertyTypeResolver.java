package io.dataspray.aws.cdk;

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
            TokenBuffer tokenBuffer = new TokenBuffer(parser, context);
            Object object = null;
            if (!path.isEmpty()) {
                object = deserializeTypedObject(parser, path, context, tokenBuffer);
            }

            if (object == null) {
                object = _deserializeTypedUsingDefaultImpl(parser, context, tokenBuffer);
            }

            return object;
        }

        private Object deserializeTypedObject(JsonParser parser, List<String> path, DeserializationContext context, TokenBuffer tokenBuffer) throws IOException {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Type id path can't be null or empty");
            }

            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                token = parser.nextToken();
                while (token == JsonToken.FIELD_NAME) {
                    String fieldName = parser.currentName();
                    token = parser.nextToken();

                    if (fieldName.equals(path.get(0)) && path.size() == 1 && token != JsonToken.VALUE_NULL &&
                            token.isScalarValue()) {
                        return _deserializeTypedForId(parser, context, tokenBuffer);
                    }

                    if (fieldName.equals(path.get(0)) && path.size() > 1 && token == JsonToken.START_OBJECT) {
                        tokenBuffer.writeFieldName(fieldName);
                        tokenBuffer.writeStartObject();
                        Object object = deserializeTypedObject(parser, path.subList(1, path.size()), context, tokenBuffer);
                        if (object != null) {
                            return object;
                        }
                        tokenBuffer.writeEndObject();
                    } else {
                        tokenBuffer.writeFieldName(fieldName);
                        tokenBuffer.copyCurrentStructure(parser);
                    }

                    token = parser.nextToken();
                }
            }

            return null;
        }

    }
}
