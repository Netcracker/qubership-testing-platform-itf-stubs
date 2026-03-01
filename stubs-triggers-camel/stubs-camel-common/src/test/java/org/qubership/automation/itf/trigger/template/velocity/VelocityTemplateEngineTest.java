package org.qubership.automation.itf.trigger.template.velocity;

import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.qubership.automation.itf.core.model.common.Storable;
import org.qubership.automation.itf.core.model.jpa.context.InstanceContext;
import org.qubership.automation.itf.core.model.jpa.context.TcContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.testng.Assert;

import com.google.common.collect.Maps;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"classpath*:*template-velocity-test-context.xml"})
public class VelocityTemplateEngineTest {
    private VelocityTemplateEngine engine;

    @Before
    public void setUp() {
        engine = new VelocityTemplateEngine();
    }

    @Test
    public void testMathTool() {
        String sourceString = "$math.random(100,999)";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, sourceString, InstanceContext.from(context, null));
        Assert.assertNotEquals(processed, sourceString);
    }

    @Test
    public void testDateTool() {
        String sourceString = "#set($startDate = $date.format('yyyy-MM-dd''T''HH:mm:ss', $date))\n$startDate";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, sourceString, InstanceContext.from(context, null));
        Assert.assertFalse(processed.contains("$date"));
        System.out.println(processed);
    }

    @Test
    public void testEscTool() {
        String htmlString = "Some String with html/xml tags: <customer><name>Alex</name><id>54321</id></customer>";
        String velocityString = "$esc.html(\"" + htmlString + "\")";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, velocityString, InstanceContext.from(context, null));
        Assert.assertEquals(processed, StringEscapeUtils.escapeHtml4(htmlString));
    }

    @Test
    public void testNumberTool() {
        String velocityString = "#set($num = 55.666)\n$number.format(\"#0000\", $num)";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, velocityString, InstanceContext.from(context, null));
        Assert.assertEquals(processed, "0056");
    }

    @Test
    public void testXmlTool() {
        String velocityString = "#set($myXML = $xml.parse($tc.response))\n"
                + "#foreach($eventFragment in $myXML.children().iterator())\n"
                + "$eventFragment\n"
                + "#end";
        TcContext context = new TcContext();
        context.put("response", "<note><to>Tove</to><from>Jani</from><heading>Reminder</heading><body>Don't forget me this weekend!</body></note>");
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, velocityString, InstanceContext.from(context, null));
        Assert.assertEquals(processed, "<to>Tove</to>\n<from>Jani</from>\n<heading>Reminder</heading>\n<body>Don't forget me this weekend!</body>\n");
    }
}
