package io.linguarobot.aws.cdk.maven.node;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


public class NodeVersionTest {

    @DataProvider
    public Object[][] parseDataProvider() {
        return new Object[][]{
                {"v0.9.12", NodeVersion.of(0, 9, 12)},
                {"v10.0.0", NodeVersion.of(10, 0, 0)},
                {"v14.2.0", NodeVersion.of(14, 2, 0)},
                {"v14.2.0\n", NodeVersion.of(14, 2, 0)},
                {" v14.2.0 ", NodeVersion.of(14, 2, 0)},
                {"14.2.0", null},
                {"v14,2,0", null},
                {"v14.2", null}
        };
    }

    @Test(dataProvider = "parseDataProvider")
    public void testParse(String version, NodeVersion expectedVersion) {
        NodeVersion nodeVersion = NodeVersion.parse(version).orElse(null);
        Assert.assertEquals(nodeVersion, expectedVersion);
    }

    @DataProvider
    public Object[][] compareToDataProvider() {
        return new Object[][]{
                {NodeVersion.of(14, 2, 0), NodeVersion.of(14, 0, 0), 1},
                {NodeVersion.of(0, 9, 12), NodeVersion.of(1, 0, 0), -1},
                {NodeVersion.of(14, 2, 0), NodeVersion.of(14, 2, 0), 0},
                {NodeVersion.of(14, 2, 0), NodeVersion.of(14, 2, 1), -1}
        };
    }

    @Test(dataProvider = "compareToDataProvider")
    public void testCompareTo(NodeVersion left, NodeVersion right, int expectedResult) {
        Assert.assertEquals(left.compareTo(right), expectedResult);
    }

}
