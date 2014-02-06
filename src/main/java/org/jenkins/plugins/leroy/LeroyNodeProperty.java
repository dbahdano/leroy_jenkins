/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkins.plugins.leroy;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.ComputerSet;
import hudson.model.Environment;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import org.kohsuke.stapler.QueryParameter;
import java.io.File;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkins.plugins.leroy.util.XMLParser;

/**
 * {@link NodeProperty} that sets additional node properties.
 *
 * @since 1.286
 */
public class LeroyNodeProperty extends NodeProperty<Node> {
    
    private String leroyhome;
    
    private String leroycontrollerport;
   
    public static List<String> agents = new ArrayList<String>();
    
    public static List<String> environments = new ArrayList<String>();
    
    public static List<String> roles = new ArrayList<String>();
    
    @DataBoundConstructor
    public LeroyNodeProperty(String leroyhome, String leroycontrollerport) {
        this.leroyhome = leroyhome; 
        this.leroycontrollerport = leroycontrollerport;
        
        
        String filepath = leroyhome + "\\agents.xml";
                agents =  XMLParser.getAgents(new File(filepath));
                
                String filepath1 = leroyhome + "\\environments\\";
        
                //get file names
                List<String> results = new ArrayList<String>();
                File[] files = new File(filepath1).listFiles();

                for (File file : files) {
                    if (file.isFile()) {
                        results.add(file.getName());
                    }
                }

                List<String> envs = new ArrayList<String>();
                List<String> envsroles = new ArrayList<String>();
                
                for(String fname : results)
                {
                    XMLParser.getEnvironment(new File(filepath1+fname));
                    envs.addAll(XMLParser.getEnvironment(new File(filepath1+fname)));
                    envsroles.addAll(XMLParser.getRoles(new File(filepath1+fname)));
                } 

                environments = envs;
                roles = envsroles;
    }
	
    public String getLeroyhome() {
        return leroyhome;
    }
   
    public String getLeroycontrollerport() {
        return leroycontrollerport;
    }
    
    public List<String> getList(){
        return agents;
    }
    
    public List<String> getAgentList(){
        String filepath = getLeroyhome() + "\\agents.xml";
        return XMLParser.getAgents(new File(filepath));
    }
    
    public List<String> getEnvironmentList(){
        String filepath = getLeroyhome() + "\\environment\\";
        
        //get file names
        List<String> results = new ArrayList<String>();
        File[] files = new File(filepath).listFiles();

        for (File file : files) {
            if (file.isFile()) {
                results.add(file.getName());
            }
        }
        
        List<String> envs = new ArrayList<String>();
        for(String fname : results)
        {
            envs.addAll(XMLParser.getAgents(new File(filepath+fname)));
        } 
        
        return envs;
    }
    
    
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
           EnvVars env = new EnvVars();
           env.put("IS_LEROY_NODE", "TRUE");
           env.put("LEROY_HOME", getLeroyhome());
           env.put("LEROY_CONTROLLER_PORT", getLeroycontrollerport());
           return Environment.create(env);
    }

    @Override
    public void buildEnvVars(EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        env.put("IS_LEROY_NODE", "TRUE");
        env.put("LEROY_HOME", getLeroyhome());
        env.put("LEROY_CONTROLLER_PORT", getLeroycontrollerport());
    }

    /**
     *
     */
    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
	public String getDisplayName() {
			return "Leroy Host";
	}

        public ListBoxModel doFillGoalTypeItems() {
            ListBoxModel items = new ListBoxModel();
            for (String agent : agents) {
                items.add(agent,agent);
            }
             return items;
        }
        
        public ListBoxModel doFillRolesItems() {
            ListBoxModel items = new ListBoxModel();
            for (String role : roles) {
                items.add(role,role);
            }
             return items;
        }
        
        public ListBoxModel doFillEnvironmentItems() {
            ListBoxModel items = new ListBoxModel();
            for (String env : environments) {
                items.add(env,env);
            }
            return items;
        }
        
        public FormValidation doAddAgent(@QueryParameter("leroyhome") final String leroyhome, 
                @QueryParameter("agentname") final String agentname ) 
                throws IOException, ServletException {
            try {
                Launcher launcher = Hudson.getInstance().createLauncher(TaskListener.NULL);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                //FilePath projectRoot = build.getWorkspace();
                int returnCode = 0;
                
                if(launcher.isUnix())
                    returnCode = launcher.launch().cmds(leroyhome+"/controller","--addagent", agentname).stdout(output).join();
                else
                    returnCode = launcher.launch().cmds(leroyhome+"/controller.exe","--addagent", agentname).stdout(output).join();
                
                
                if(returnCode==0)
                {
                    return FormValidation.ok("Success");
                }
                
                return FormValidation.error("Failed to add agent");
                
            } catch (Exception e) {
                return FormValidation.error("Client error : "+e.getMessage());
            }
            
        }
        
         public FormValidation doCheckLeroyhome(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please provide a path for leroy plugin");
            else {
                String filepath = value + "\\agents.xml";
                agents =  XMLParser.getAgents(new File(filepath));
                
                String filepath1 = value + "\\environments\\";
        
                //get file names
                List<String> results = new ArrayList<String>();
                File[] files = new File(filepath1).listFiles();

                for (File file : files) {
                    if (file.isFile()) {
                        results.add(file.getName());
                    }
                }

                List<String> envs = new ArrayList<String>();
                List<String> envsroles = new ArrayList<String>();
                
                for(String fname : results)
                {
                    XMLParser.getEnvironment(new File(filepath1+fname));
                    envs.addAll(XMLParser.getEnvironment(new File(filepath1+fname)));
                    envsroles.addAll(XMLParser.getRoles(new File(filepath1+fname)));
                } 

                environments = envs;
                roles = envsroles;

//                doFillRolesItems();
//                doFillEnvironmentItems();
//                doFillGoalTypeItems();
            }
            return FormValidation.ok();
        }
        public String getHelpPage() {
            // yes, I know this is a hack.
            ComputerSet object = Stapler.getCurrentRequest().findAncestorObject(ComputerSet.class);
           
            if (object != null) {
                
                // we're on a node configuration page, show show that help page
                return "/help/system-config/nodeEnvironmentVariables.html";
            } else {
                // show the help for the global config page
                return "/help/system-config/globalEnvironmentVariables.html";
            }
        }
    }

}
