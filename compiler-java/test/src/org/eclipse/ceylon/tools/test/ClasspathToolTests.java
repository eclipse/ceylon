/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.eclipse.ceylon.tools.test;

import java.util.Collections;

import org.eclipse.ceylon.common.Versions;
import org.eclipse.ceylon.common.tool.OptionArgumentException;
import org.eclipse.ceylon.common.tool.ToolError;
import org.eclipse.ceylon.common.tool.ToolModel;
import org.eclipse.ceylon.common.tool.ToolUsageError;
import org.eclipse.ceylon.tools.classpath.CeylonClasspathTool;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class ClasspathToolTests extends AbstractToolTests {
    
    @Test
    public void testNoArgs() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        try {
            CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), Collections.<String>emptyList());
            Assert.fail();
        } catch (OptionArgumentException e) {
            // asserting this is thrown
        }
    }
    
    @Test
    public void testRecursiveDependencies() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), 
                toolOptions("io.cayla.web/0.3.0"));
        try{
            tool.run();
        }catch(ToolError err){
            Assert.assertEquals("Module conflict error prevented classpath generation: try running \"ceylon info --print-overrides io.cayla.web/0.3.0\" to display an override file you can use with \"ceylon classpath --overrides override.xml io.cayla.web/0.3.0\" or try with \"ceylon classpath --force io.cayla.web/0.3.0\" to select the latest versions", err.getMessage());
        }
    }

    @Ignore("Disabled due to backward compat break")
    @Test
    public void testRecursiveDependenciesOverride() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), 
                toolOptions("--overrides", getPackagePath()+"/overrides.xml", "io.cayla.web/0.3.0"));
        StringBuilder b = new StringBuilder();
        tool.setOut(b);
        tool.run();
        String cp = b.toString();
        Assert.assertTrue(cp.contains("org.jboss.logging-3.1.3.GA.jar"));
        Assert.assertFalse(cp.contains("org.jboss.logging-3.1.2.GA.jar"));
    }

    @Test
    public void testRecursiveDependenciesForce() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), 
                toolOptions("--force", "io.cayla.web/0.3.0"));
        StringBuilder b = new StringBuilder();
        tool.setOut(b);
        tool.run();
        String cp = b.toString();
        Assert.assertTrue(cp.contains("org.jboss.logging-3.1.3.GA.jar"));
        Assert.assertFalse(cp.contains("org.jboss.logging-3.1.2.GA.jar"));
    }

    @Test
    public void testMissingModule() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), 
                toolOptions("naskduhqwedmansd"));
        try{
            tool.run();
            Assert.fail();
        }catch(ToolUsageError x){
            Assert.assertTrue(x.getMessage().contains("Module naskduhqwedmansd not found"));
        }
    }

    @Test
    public void testModuleNameWithBadVersion() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), 
                toolOptions("ceylon.language/666"));
        try{
            tool.run();
        }catch(ToolUsageError x){
            Assert.assertTrue(x.getMessage().contains("Version 666 not found for module ceylon.language"));
        }
    }

    @Test
    public void testNoOptionalModules() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), 
                toolOptions("ceylon.language/"+Versions.CEYLON_VERSION_NUMBER));
        StringBuilder b = new StringBuilder();
        tool.setOut(b);
        tool.run();
        String cp = b.toString();
        Assert.assertTrue(cp.contains("ceylon.language-"+Versions.CEYLON_VERSION_NUMBER+".car"));
        Assert.assertFalse(cp.contains("minidev"));
//        Assert.assertFalse(cp.contains("maven"));
        Assert.assertFalse(cp.contains("aether"));
//        Assert.assertFalse(cp.contains("plexus"));
    }

    @Test
    public void testWithOptionalModules() throws Exception {
        ToolModel<CeylonClasspathTool> model = pluginLoader.loadToolModel("classpath");
        Assert.assertNotNull(model);
        CeylonClasspathTool tool = pluginFactory.bindArguments(model, getMainTool(), 
                toolOptions(
                        "ceylon.language/"+Versions.CEYLON_VERSION_NUMBER,
                        "org.eclipse.ceylon.module-resolver-aether/"+Versions.CEYLON_VERSION_NUMBER));
        StringBuilder b = new StringBuilder();
        tool.setOut(b);
        tool.run();
        String cp = b.toString();
        Assert.assertTrue(cp.contains("ceylon.language-"+Versions.CEYLON_VERSION_NUMBER+".car"));
        Assert.assertFalse(cp.contains("minidev"));
//        Assert.assertTrue(cp.contains("maven"));
        Assert.assertTrue(cp.contains("aether"));
//        Assert.assertTrue(cp.contains("plexus"));
    }
}
