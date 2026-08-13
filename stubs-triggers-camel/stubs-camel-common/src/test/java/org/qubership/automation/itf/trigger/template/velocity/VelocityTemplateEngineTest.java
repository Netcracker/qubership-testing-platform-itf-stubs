package org.qubership.automation.itf.trigger.template.velocity;

import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.qubership.automation.itf.core.model.common.Storable;
import org.qubership.automation.itf.core.model.jpa.context.InstanceContext;
import org.qubership.automation.itf.core.model.jpa.context.TcContext;

import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.google.common.collect.Maps;

@SpringJUnitConfig(locations = {"classpath*:*template-velocity-test-context.xml"})
public class VelocityTemplateEngineTest {
    private VelocityTemplateEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new VelocityTemplateEngine();
    }

    @Test
    public void testMathTool() {
        String sourceString = "$math.random(100,999)";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, sourceString, InstanceContext.from(context, null));
        Assertions.assertNotEquals(sourceString, processed);
    }

    @Test
    public void testDateTool() {
        String sourceString = "#set($startDate = $date.format('yyyy-MM-dd''T''HH:mm:ss', $date))\n$startDate";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, sourceString, InstanceContext.from(context, null));
        Assertions.assertFalse(processed.contains("$date"));
        System.out.println(processed);
    }

    @Test
    public void testEscTool() {
        String htmlString = "Some String with html/xml tags: <customer><name>Alex</name><id>54321</id></customer>";
        String velocityString = "$esc.html(\"" + htmlString + "\")";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, velocityString, InstanceContext.from(context, null));
        Assertions.assertEquals(StringEscapeUtils.escapeHtml(htmlString), processed);
    }

    @Test
    public void testNumberTool() {
        String velocityString = "#set($num = 55.666)\n$number.format(\"#0000\", $num)";
        TcContext context = new TcContext();
        Map<String, Storable> map = Maps.newHashMap();
        String processed = engine.process(map, velocityString, InstanceContext.from(context, null));
        Assertions.assertEquals("0056", processed);
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
        Assertions.assertEquals("<to>Tove</to>\n<from>Jani</from>\n<heading>Reminder</heading>\n<body>Don't forget me this weekend!</body>\n", processed);
    }
}
