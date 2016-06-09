package cl.kaiser.hana;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Properties;

import org.apache.commons.dbcp.BasicDataSource;

import com.google.gson.JsonObject;


public class Demo {

	public static void main(String[] args) throws IOException {
		
		//Settings by file
        Properties prop = new Properties();
        InputStream input = new FileInputStream("config.propierties");
        prop.load(input);
        String dbURL  = prop.getProperty("URL");
        String dbprop = prop.getProperty("PROP");
        String dbdrv  = prop.getProperty("DRIVER");
        String dbqry  = prop.getProperty("QVAL");
        String user = prop.getProperty("User");
        String pass = prop.getProperty("Pass");		

        //Datasource
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(dbdrv);
        ds.setUsername(user);
        ds.setPassword(pass);
        ds.setUrl(dbURL);
        ds.setValidationQuery(dbqry);
        ds.setConnectionProperties(dbprop);
        ds.setDefaultAutoCommit(false);
        
        //Example
		try {
			 Connection con = ds.getConnection();
	        String pSchema="NEO_9CHCY0XDJBPAEVS15X1S8UZL7";
	        String pProcedure="demo_sp";        
	        KProcedure spDemo=new KProcedure(pSchema, pProcedure);
	        String dataStrIn = Utils.readFile("input.json", StandardCharsets.UTF_8);	              
	    	JsonObject call=spDemo.call(con, dataStrIn);
	    	String definition=spDemo.getJsonDefinition();
	    	System.out.println("DEF-1:\n"+definition);
	    	System.out.println("CALL:\n"+call.toString());

	    	System.out.println("== FROM CACHE ==");
	        KProcedure spDemoCache=new KProcedure(pSchema, pProcedure);
	        spDemoCache.loadDefinitionFromJson(definition);
	    	JsonObject call2=spDemo.call(con, dataStrIn);
	    	System.out.println("DEF-2:\n"+spDemoCache.getJsonDefinition());	    	
	    	System.out.println("CALL:\n"+call2.toString());
	        
	    	
	        			
		} catch (Exception e) {
			e.printStackTrace();
		}
        System.out.println("== END ==");
	}

}
