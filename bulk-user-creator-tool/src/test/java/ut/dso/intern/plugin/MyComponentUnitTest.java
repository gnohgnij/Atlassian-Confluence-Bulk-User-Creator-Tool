package ut.dso.intern.plugin;

import org.junit.Test;
import dso.intern.plugin.api.MyPluginComponent;
import dso.intern.plugin.impl.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}