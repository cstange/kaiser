package cl.kaiser.hana;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Utils {

	/**
	 * Convert ResultSet to List
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	public final static List<HashMap<String,Object>> resultSetToArrayList(ResultSet rs) throws SQLException{
	  ResultSetMetaData md = rs.getMetaData();
	  int columns = md.getColumnCount();
	  ArrayList<HashMap<String,Object>> list = new ArrayList<HashMap<String,Object>>(50);
	  while (rs.next()){
	     HashMap<String,Object> row = new HashMap<String,Object>(columns);
	     for(int i=1; i<=columns; ++i){           
	      row.put(md.getColumnName(i),rs.getObject(i));
	     }
	     list.add(row);
	  }
	  return list;
	}	

	/**
	 * 
	 * @param path
	 * @param encoding
	 * @return
	 * @throws IOException
	 */
	public final static String readFile(String path, Charset encoding) throws IOException 
	{
	  byte[] encoded = Files.readAllBytes(Paths.get(path));
	  return new String(encoded, encoding);
	}	
}
