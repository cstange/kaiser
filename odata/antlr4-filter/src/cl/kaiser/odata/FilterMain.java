package cl.kaiser.odata;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import cl.kaiser.odata.gen.OdataFilterLexer;
import cl.kaiser.odata.gen.OdataFilterParser;

public class FilterMain {

	public static void main(String[] args) throws SQLException, IOException {
		
	  Connection c = null;
	  try {
	     Class.forName("org.postgresql.Driver");
	     c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/dvdrental","demo", "demo");
	  } catch (Exception e) {
	     e.printStackTrace();
	     System.err.println(e.getClass().getName()+": "+e.getMessage());
	     System.exit(0);
	  }
	  System.out.println("Opened database successfully");		
	
	  long mseg=System.currentTimeMillis();
	  OdataImpSql odata=new OdataImpSql();	  	  
	  OutputStream out = new FileOutputStream(new File("demo.json"));
	  odata.QueryJsonObject(c, out, null,null, "rental","rental_id ne 0",null,15000,-1,true);
	  out.close();	  
	  System.out.println("\n\nDURA:"+(System.currentTimeMillis()-mseg));
		
	}
	
	public static void simpleParser(String filter) {
		System.out.println("---------------------");
		System.out.println("FILTER INPUT="+filter);
		ANTLRInputStream input = new ANTLRInputStream(filter);
		OdataFilterLexer lexer = new OdataFilterLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		OdataFilterParser parser = new OdataFilterParser(tokens);
		ParseTree tree = parser.prog();
		OdataFilterSql eval = new OdataFilterSql();
		System.out.println("QUERY WHERE ="+eval.visit(tree));		
	}

}
