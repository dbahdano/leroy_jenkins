package org.jenkins.plugins.leroy;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import static java.nio.file.StandardCopyOption.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkins.plugins.leroy.util.XMLParser;

/**
 * <p>
 * Leory builder to perform deploy step 
 * </p>
 * @author Yunus Dawji
 */
public class LeroyBuilder extends Builder {

    private  String envrn;
    
    private String workflow;
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public LeroyBuilder(String leroyhome, String envrn, String workflow) {
        
        this.envrn = envrn;
        this.workflow = workflow;
    }

    
    /**
     * Get LEROY_HOME
     */
    public static String  getLeroyhome() throws InterruptedException, IOException {
        Jenkins jenkins = Jenkins.getInstance();
        Computer[] computers = jenkins.getComputers();
        
        for(int i = 0; i < computers.length; i++)
        {
             EnvVars envs = computers[i].buildEnvironment(TaskListener.NULL); 
             if(envs.containsKey("IS_LEROY_NODE"))
             {
                 return envs.get("LEROY_HOME");
             }
        }
        
        return null;
    }
    
    /**
     * Get Workflow
     * @return 
     */
    public String getWorkflow() {
        return workflow;
    }
   
    /**
     * Get Environment
     * @return 
     */
    public String getEnvrn() {
        return envrn;
    }
  
   
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        List<JobPropertyDescriptor> jobPropertyDescriptors = Functions.getJobPropertyDescriptors(NewProject.class);
        EnvVars envs = build.getEnvironment(listener);
        FilePath projectRoot = build.getWorkspace();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        String leroypath = envs.expand(this.getLeroyhome());
//        String envrn = envs.get("environment");
//        String workflow = envs.get("workflow");
        
        
        listener.getLogger().println("LEROY_HOME: " + leroypath);
        
        int returnCode;
     
        if(launcher.isUnix())
        {   
            returnCode = launcher.launch().envs(envs).cmds("cp" ,"-fR",".", leroypath).stdout(output).pwd(projectRoot).join();
            listener.getLogger().println(output.toString().trim());
            
            
            if(returnCode==0)
            {
                returnCode = launcher.launch().envs(envs).cmds("sh", Hudson.getInstance().getRootDir() + "/plugins/leroy/deploy.sh", leroypath ,this.workflow, this.envrn).stdout(listener.getLogger()).pwd(projectRoot).join();
                listener.getLogger().println(output.toString().trim());
            }
        }
        else
        { 
            returnCode = launcher.launch().envs(envs).cmds(Hudson.getInstance().getRootDir() + "/plugins/leroy/deploy.bat", "." ,leroypath, this.workflow, this.envrn).stdout(output).pwd(projectRoot).join();
            listener.getLogger().println(output.toString().trim());
        
            
//            if(returnCode==0)
//            {
//                returnCode = launcher.launch().envs(envs).cmds("controller.exe","--workflow", this.workflow,"--environment", this.envrn).stdout(listener.getLogger()).pwd(leroypath).join();
//                listener.getLogger().println(output.toString().trim());
//            }
        }
//        if(returnCode==0)
//        {
//            listener.getLogger().println("Building Artifacts");         
//            Date currenttime = new Date();
//            String build_number = envs.get("BUILD_NUMBER");
//            String date = "";
//            String month = "";
//            
//            if(currenttime.getDate() < 10)
//            {
//                date = "0" + currenttime.getDate();
//            }
//            else
//            {
//                date = ""+currenttime.getDate();
//            }
//            if(currenttime.getMonth()< 10)
//            {
//                month = "0" + currenttime.getMonth();
//            }
//            else
//            {
//                month = ""+currenttime.getMonth();
//            }
//            
//            String zipFile = projectRoot.toString()+"\\zip\\" + build_number + "_" + workflow + "_" + envrn+ "_"
//                    + date +month+currenttime.getYear()+" "+currenttime.getHours()+
//                    "_"+currenttime.getMinutes()+"_"+currenttime.getSeconds()+".zip";
//		
//		String srcDir = projectRoot.toString();
//		     projectRoot.zip(new FilePath(new File(zipFile)));
//
//            listener.getLogger().println("Copying Artifacts");         
//            
//            returnCode = launcher.launch().envs(envs).cmds("xcopy" ,".\\zip", leroypath+"\\artifacts", "/E", "/R" ,"/Y").stdout(output).pwd(projectRoot).join();
//            listener.getLogger().println(output.toString().trim());
//            
//            
//                    
//            //returnCode = launcher.launch().envs(envs).cmds(leroypath+"/controller.exe","--workflow", workflow,"--environment", envrn).stdout(listener.getLogger()).pwd(leroypath).join();
//        }
        
        return returnCode==0;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }
    
    
    
    private static void addDirToArchive(ZipOutputStream zos, File srcFile) {

		File[] files = srcFile.listFiles();

		System.out.println("Adding directory: " + srcFile.getName());

		for (int i = 0; i < files.length; i++) {
			
			// if the file is directory, use recursion
			if (files[i].isDirectory()) {
				addDirToArchive(zos, files[i]);
				continue;
			}

			try {
				
				System.out.println("Adding file: " + files[i].getName());

				// create byte buffer
				byte[] buffer = new byte[1024];

				FileInputStream fis = new FileInputStream(files[i]);

				zos.putNextEntry(new ZipEntry(files[i].getName()));
				
				int length;

				while ((length = fis.read(buffer)) > 0) {
					zos.write(buffer, 0, length);
				}

				zos.closeEntry();

				// close the InputStream
				fis.close();

			} catch (IOException ioe) {
				System.out.println("IOException :" + ioe);
			}
			
		}

	}
    
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
         public DescriptorImpl() {
             load();
        }
        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckLeroyhome(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please provide a path for leroy plugin");
            return FormValidation.ok();
        }
        
        
        public ListBoxModel doFillEnvrnItems() {
            ListBoxModel items = new ListBoxModel();
            
            String envspath = "";
            
            try {
                envspath = LeroyBuilder.getLeroyhome() + "/environments.xml";
            } catch (InterruptedException ex) {
                Logger.getLogger(LeroyBuilder.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(LeroyBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
            List<String> envsroles = XMLParser.getEnvironment(new File(envspath));
            
            for (String envs : envsroles) {
                items.add(envs,envs);
            }
            return items;
            
        }
        
        public ListBoxModel doFillWorkflowItems() {
            ListBoxModel items = new ListBoxModel();
            
            String workflowpath = "";
            
            try {
                workflowpath = LeroyBuilder.getLeroyhome()+"/workflows/";
            } catch (InterruptedException ex) {
                Logger.getLogger(LeroyBuilder.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(LeroyBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            //get file names
            List<String> results = new ArrayList<String>();
            File[] files = new File(workflowpath).listFiles();

            if(files.length > 0)
            {
                for (File file : files) {
                    if (file.isFile() && file.getName().contains(".xml")) {
                        results.add(file.getName().substring(0, file.getName().length()-4));
                    }
                    if (file.isDirectory()) {
                       
                        File[] files1 = new File(workflowpath).listFiles();
                        if(files.length > 0)
                        {
                            for (File file1 : files1) {
                                if (file1.isFile() && file1.getName().contains(".xml")) {
                                    results.add(file.getName()+"/"+file1.getName().substring(0, file1.getName().length()-4));
                                }
                            }
                        }
                    }
                }
            }

            for (String role : results) {
                items.add(role,role);
            }
            return items;
        }
      
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Leroy";
        }
        
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req,formData);
        }

    }
}

