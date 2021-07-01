package dso.intern.plugin;

import javax.inject.Named;

import java.lang.Iterable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Hashtable;
 
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContext;

import com.atlassian.confluence.user.DefaultUserAccessor;
import com.atlassian.user.impl.DefaultUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.user.security.password.Credential;
import com.atlassian.confluence.user.ConfluenceUser;    
import com.atlassian.user.Group;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

@Named("BulkUserCreatorToolServlet")
public class BulkUserCreatorToolServlet extends HttpServlet{

    private final UserAccessor userAccessor;

    public BulkUserCreatorToolServlet(UserAccessor userAccessor){
        this.userAccessor = userAccessor;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        // Create a factory for disk-based file items
        DiskFileItemFactory factory = new DiskFileItemFactory();

        // Configure a repository (to ensure a secure temp location is used)
        ServletContext servletContext = this.getServletConfig().getServletContext();
        File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
        factory.setRepository(repository);

        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);

        try{
            //Parse the request to get file items
            List<FileItem> fileItems = upload.parseRequest(request);

            // Process the uploaded items
            Iterator<FileItem> iter = fileItems.iterator();
            while(iter.hasNext()){
                FileItem item = iter.next();

                if(!item.isFormField()){
                    String content = item.getString();
                    StringReader sReader = new StringReader(content);
                    
                    Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(sReader);

                    Hashtable<String, String> errors = new Hashtable<String, String>();

                    for(CSVRecord record : records){

                        boolean canCreateUser = true;

                        String username;
                        String fullname;
                        String email;
                        String password;
                        String groupsToBeAddedInto;

                        //check is record size matches header size
                        if(!record.isConsistent()){
                            canCreateUser = false;
                            errors.put("Row " + record.getRecordNumber(), "Contains empty field(s)");
                        }
                        else{
                            //get username, fullname, email, password and groupsToBeAddedInto
                            username = record.get("Username");    //UTF-8 BOM
                            fullname = record.get("Fullname");
                            email = record.get("Email");
                            password = record.get("Password");
                            groupsToBeAddedInto = record.get("GroupsToBeAddedInto");

                            String[] groupArray = groupsToBeAddedInto.split(",");


                            //check if username already exist
                            if(userAccessor.exists(username)){
                                canCreateUser = false;
                                errors.put(username, "User " + username + " already exists");
                            }

                            //check if group exists
                            List<Group> listOfGroups = userAccessor.getGroupsAsList();
                            String[] listOfGroupNames = new String[listOfGroups.size()];
                            int j=0;
                            for(Group g : listOfGroups){
                                listOfGroupNames[j] = g.getName();
                                j++;
                            }
                            if(listOfGroupNames.length > 0){
                                for(String s : groupArray){
                                    boolean contains = Arrays.stream(listOfGroupNames).anyMatch(s::equals);
                                    if(!contains){
                                        canCreateUser = false;
                                        errors.put(username, "Group " + s + " does not exist");
                                    }
                                }
                            }

                            if(canCreateUser){
                                DefaultUser defaultUser = new DefaultUser(username, fullname, email);
                                ConfluenceUser newUser = userAccessor.createUser(defaultUser, Credential.unencrypted(password));
                                userAccessor.addMembership(UserAccessor.GROUP_CONFLUENCE_USERS, username);  //add to confluence users group to enable login
                                for(int i=0; i<groupArray.length; i++){
                                    userAccessor.addMembership(groupArray[i], username);
                                }
                            }
                        }
                    }

                    if(errors.isEmpty()){
                        response.setContentType("text/html; charset=UTF-8");

                        PrintWriter out = response.getWriter();
                        out.println("<!DOCTYPE html>");
                        out.println("<html><head>");
                        out.println("<meta name=\"decorator\" content=\"atl.admin\" />");
                        out.println("<title>Bulk User Creation Tool</title></head>");
                        out.println("<body style=\"text-align: center;\"><h3>User(s) created successfully!</h3></body></html>");
                        out.close();
                    }
                    else{
                        response.setContentType("text/html; charset=UTF-8");

                        PrintWriter out = response.getWriter();
                        out.println("<!DOCTYPE html>");
                        out.println("<html><head>");
                        out.println("<meta name=\"decorator\" content=\"atl.admin\" />");
                        out.println("<title>Bulk User Creation Tool</title></head>");
                        out.println("<body style=\"text-align: center;\">");

                        errors.forEach((k,v) -> {
                            out.println("<p>" + k + ": " + v + "</p>\r\n");
                        });
                        
                        out.println("</body></html>");
                        out.close();
                    }
                }
            }
            
            

        }
        catch(FileUploadException e){
            e.printStackTrace();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}