package cl.kaiser.hana;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Utils {

	/*
	 * Convert ResultSet to List 
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
}
