package org.jenkinsci.jira_versions;

import hudson.plugins.jira.soap.JiraSoapService;
import hudson.plugins.jira.soap.RemoteAuthenticationException;
import hudson.plugins.jira.soap.RemoteComponent;
import hudson.plugins.jira.soap.RemoteProject;
import hudson.plugins.jira.soap.RemoteVersion;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.rpc.ServiceException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.jira.JIRA;
import org.jvnet.hudson.update_center.DefaultMavenRepositoryBuilder;
import org.jvnet.hudson.update_center.HudsonWar;
import org.jvnet.hudson.update_center.AlphaBetaOnlyRepository;
import org.jvnet.hudson.update_center.ConfluencePluginList;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenRepository;
import org.jvnet.hudson.update_center.Plugin;
import org.jvnet.hudson.update_center.PluginHistory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class Main {
    @Option(name="-no-experimental",usage="Exclude alpha/beta releases")
    public boolean noExperimental;
    
    @Option(name="-jiraBaseUrl",
            usage="The base URL for the JIRA instance to add versions to")
    public String jiraBaseUrl;

    public static void main( String[] args ) throws Exception {
        System.exit(new Main().run(args));        
    }
    
    public int run(String[] args) throws Exception {
        CmdLineParser p = new CmdLineParser(this);
        try {
            p.parseArgument(args);

            run();
            return 0;
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            p.printUsage(System.err);
            return 1;
        }
    }
    
    public void run() throws Exception {
        MavenRepository repo = createRepository();
        JiraSoapService jira = JIRA.connect(new URL(jiraBaseUrl));
        String token = loginToJira(jira);
        
        List<String> allVersions = new ArrayList<String>();
        RemoteVersion[] versions = jira.getVersions(token, "JENKINS");
        for(RemoteVersion version : versions) {
            allVersions.add(version.getName());
        }
        
        addCoreVersions(jira, token, repo, allVersions);
        addPluginVersions(jira, token, repo, allVersions);
    }
    
    private String loginToJira(JiraSoapService jira) throws RemoteException, IOException {
        File f = new File(new File(System.getProperty("user.home")),".jenkins-ci.org");
        String jiraUsername = "", jiraPassword = "";
        if(f.isFile()) {            
            Properties props = new Properties();
            props.load(new FileInputStream(f));
            jiraUsername = props.getProperty("userName");
            jiraPassword = props.getProperty("password");
        }
        return jira.login(jiraUsername, jiraPassword);
    }    
   
    private void addCoreVersions(JiraSoapService jira, String token, MavenRepository repository, List<String> allVersions) throws Exception {
        TreeMap<VersionNumber,HudsonWar> wars = repository.getHudsonWar();
        if (wars.isEmpty())     return;
        
        for(VersionNumber war : wars.keySet()) {
            HudsonWar current = wars.get(war);
            String coreVersion = "jenkins-" + current.version;
            
            if(allVersions.contains(coreVersion)) continue;
            
            RemoteVersion newCoreVersion = new RemoteVersion();
            newCoreVersion.setName(coreVersion);
            newCoreVersion.setReleased(true);
            Calendar releaseDate = Calendar.getInstance();
            releaseDate.setTime(current.getTimestampAsDate());
            newCoreVersion.setReleaseDate(releaseDate);
            
            boolean added = false;
            while (!added) {
                try {
                    jira.addVersion(token, "JENKINS", newCoreVersion);
                    added = true;
                } catch (RemoteAuthenticationException e) {
                    token = loginToJira(jira);
                }
            }
            allVersions.add(coreVersion);
        }        
    }
    
    private void addPluginVersions(JiraSoapService jira, String token, MavenRepository repository, List<String> allVersions) throws Exception {
        ConfluencePluginList cpl = new ConfluencePluginList();
        
        for( PluginHistory hpi : repository.listHudsonPlugins() ) {
            try {
                System.out.println(hpi.artifactId);

                Plugin plugin = new Plugin(hpi,cpl);
                if (plugin.isDeprecated()) {
                    System.out.println("=> Plugin is deprecated.. skipping.");
                    continue;
                }
                
                for(VersionNumber pluginVersion : hpi.artifacts.keySet()) {
                    HPI current = hpi.artifacts.get(pluginVersion);
                    
                    String artifactId = current.artifact.artifactId;
                    artifactId = artifactId.replaceAll("-plugin$", "");
                    String pluginVersionStr = artifactId + "-" + current.version;
                    
                    if(allVersions.contains(pluginVersionStr)) continue;
                    
                    RemoteVersion newPluginVersion = new RemoteVersion();
                    newPluginVersion.setName(pluginVersionStr);
                    newPluginVersion.setReleased(true);
                    Calendar releaseDate = Calendar.getInstance();
                    releaseDate.setTime(current.getTimestampAsDate());
                    newPluginVersion.setReleaseDate(releaseDate);
                    
                    boolean added = false;
                    while(!added) {
                        try {
                            jira.addVersion(token, "JENKINS", newPluginVersion);
                            added = true;
                        } catch(RemoteAuthenticationException re) {
                            token = loginToJira(jira);
                        }
                    }
                    allVersions.add(pluginVersionStr);
                }
            } catch (IOException e) {
                e.printStackTrace();
                // move on to the next plugin
            }
        }
        
    }
    
    protected MavenRepository createRepository() throws Exception {
        MavenRepository repo = DefaultMavenRepositoryBuilder.createStandardInstance();
        if (noExperimental)
            repo = new AlphaBetaOnlyRepository(repo,true);
        return repo;
    }
}
