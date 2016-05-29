package cl.kaiser.hana;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/*
 * 
 */
public class KProcedure {
	
	private Connection 	pcon;
	private String 		pSchema;
	private String 		pProcedure;

	private final String sqlProcedure	= "SELECT PARAMETER_NAME,PARAMETER_TYPE,POSITION,DATA_TYPE_NAME,HAS_DEFAULT_VALUE,TABLE_TYPE_NAME FROM SYS.PROCEDURE_PARAMETERS WHERE SCHEMA_NAME = ? AND PROCEDURE_NAME = ?  ORDER BY POSITION";
	private final String sqlType		= "SELECT COLUMN_NAME,DATA_TYPE_NAME,COLUMN_SIZE,DECIMAL_DIGITS FROM SYS.TABLE_COLUMNS_ODBC WHERE SCHEMA_NAME = ? AND TABLE_NAME = ? ORDER BY POSITION";
	private final String table_type		= "TABLE_TYPE";
	private final String table_temp		= "TEMP_";
	
	private List<KMetaObj> 		parameters;

	final private class KMetaObj {
		public String 				ParameterName;
		public String 				Type;
		public int	 				Position;
		public int	 				FixPosition;
		public String 				DataType;
		public boolean 				IsDefault;
		public String 				TableName;
		public List<KMetaTableObj>	TableColumns;		
	}
	
	final private class KMetaTableObj {
		public String 				ColumnName;
		public String 				DataType;
		public int	 				Length;
		public int					Scale;
	}	
	
	public KProcedure(Connection con,String schema,String procedureName) {
		pcon		= con;
		pSchema		= schema;
		pProcedure	= procedureName;
	}

	public void loadDefinitionFromDatabase() throws SQLException {
		parameters=new ArrayList<KMetaObj>();	
    	try (PreparedStatement st = pcon.prepareStatement(sqlProcedure);) {
    		st.setString(1, pSchema);
    		st.setString(2, pProcedure);
    		try (ResultSet rs=st.executeQuery()) {
    			while (rs.next()){
    				KMetaObj obj=new KMetaObj();
    				parameters.add(obj);
    				obj.ParameterName	= rs.getString(1);
    				obj.Type			= rs.getString(2);
    				obj.Position		= rs.getInt(3);
    				obj.DataType		= rs.getString(4);
    				obj.IsDefault		= rs.getString(5).equals("TRUE")?true:false;
    				obj.TableName		= rs.getString(6);
    				//Table as parameter
    				if (obj.DataType.equals(table_type) && obj.TableName!=null) {    					
    					try (PreparedStatement stype = pcon.prepareStatement(sqlType)) {
    						stype.setString(1, pSchema);
    						stype.setString(2, obj.TableName);
    						try (ResultSet rtype=stype.executeQuery()) {
    							obj.TableColumns=new ArrayList<KMetaTableObj>();
	    						while (rtype.next()){
	    							KMetaTableObj typeobj=new KMetaTableObj();
	    							obj.TableColumns.add(typeobj);
	    							typeobj.ColumnName		= rtype.getString(1);	
	    							typeobj.DataType		= rtype.getString(2);
	    							typeobj.Length			= rtype.getInt(3);
	    							typeobj.Scale			= rtype.getInt(4);
	    						}
    						}
    					}
    				}    				    				
    			}
    		}    		
    	}		
	}
		
	public JsonObject call(String input) throws SQLException, KProcedureException, ParseException {
		JsonParser parser 		= new JsonParser();
        JsonObject dataJsonIn	= parser.parse(input).getAsJsonObject();
        return call(dataJsonIn);
	}

	public JsonObject call(JsonObject input) throws SQLException, KProcedureException, ParseException {
       
		JsonObject 		 out			= new JsonObject();        
        String 			 sqlCall		= "";
        String 			 callParms		= "";
        List<String>	 tempDropTables	= new ArrayList<String>();
        List<String>	 tempCreaTables	= new ArrayList<String>();
        List<String>	 tempInsTables	= new ArrayList<String>();
        SimpleDateFormat formatterTS 	= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        SimpleDateFormat formatterSD 	= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat formatterDD 	= new SimpleDateFormat("yyyy-MM-dd");
                 
        //check if procedure definitions are loaded
        if (parameters==null) loadDefinitionFromDatabase();

    	//Prepare call
        int FixPosition=1;
        for (KMetaObj obj :parameters) {        
        	//Tables IN, need temp table 
        	if (obj.DataType.equals(table_type) && obj.Type.equals("IN")) {
        		String tempTableName=(table_temp+UUID.randomUUID().toString().replace("-", "")).toUpperCase();
        		tempDropTables.add(String.format("DROP TABLE #%s", tempTableName));  
        		String tempcols="";
        		for (KMetaTableObj col:obj.TableColumns) {
        			if (col.DataType.contains("CHAR"))          tempcols+=String.format("\"%s\" %s(%d),",col.ColumnName,col.DataType,col.Length);
        			else if (col.DataType.contains("DECIMAL"))  tempcols+=String.format("\"%s\" %s(%d,%d),",col.ColumnName,col.DataType,col.Length,col.Scale);
        			else tempcols+=String.format("\"%s\" %s,",col.ColumnName,col.DataType);        			
        		}
        		if (tempcols.endsWith(",")) tempcols=tempcols.substring(0, tempcols.length()-1);         		        		
        		tempCreaTables.add(String.format("CREATE LOCAL TEMPORARY TABLE #%s (%s)", tempTableName,tempcols));
        		JsonArray dataIN=input.getAsJsonArray(obj.ParameterName);
        		if (dataIN!=null) {
        			for (int i=0;i<dataIN.size();i++) {
        				JsonObject data=dataIN.get(i).getAsJsonObject();
        				String insert="";
        				for (KMetaTableObj col:obj.TableColumns) {
        					if (col.DataType.contains("CHAR") || col.DataType.contains("DATE") || col.DataType.contains("TIMESTAMP") )	{	 
        						insert+=String.format("'%s',",data.get(col.ColumnName).getAsString().replace("'", "''"));
        					}else { 										 
        						insert+=String.format("%s,",data.get(col.ColumnName).getAsString());
        					}
        				}
        				if (insert.endsWith(",")) insert=insert.substring(0, insert.length()-1); 
        				tempInsTables.add(String.format("INSERT INTO #%s values(%s)", tempTableName,insert));        				
        			}
        		} 
        		callParms+=String.format("%s => #%s,",obj.ParameterName,tempTableName);
        	//Tables OUT
        	}else if (obj.DataType.equals(table_type) && obj.Type.equals("OUT")) {
        		callParms+=String.format("%s => ?,",obj.ParameterName);
        	//Scalar types IN // OUT //INOUT
        	}else {
        		if (!(obj.IsDefault && input.get(obj.ParameterName)==null)) { 
        			callParms+=String.format("%s => ?,",obj.ParameterName);
        			obj.FixPosition=FixPosition++;
        		}
        	}
        }
        if (callParms.endsWith(",")) callParms=callParms.substring(0, callParms.length()-1); 
        
        //Execute create temp tables
        if (tempCreaTables.size()>0) {
			try (Statement stmt = pcon.createStatement();) {
				for (String temptable:tempCreaTables) { 
					stmt.addBatch(temptable);
				}
				stmt.executeBatch();
			}    
        }
		
        //Execute insert temp tables
        if (tempInsTables.size()>0) {
			try (Statement stmt = pcon.createStatement();) {
				for (String temptable:tempInsTables) { 
					stmt.addBatch(temptable);
				}
				stmt.executeBatch();
			}        
        }
        
    	//Call database procedure
    	sqlCall=String.format("{call %s.\"%s\" ( %s )} ",pSchema,pProcedure,callParms);    	
		try (CallableStatement  st = pcon.prepareCall(sqlCall)) {				
			for (KMetaObj obj :parameters) { 
				if (!obj.DataType.equals(table_type)) {
					if (obj.Type.equals("IN") || obj.Type.equals("INOUT")) {
						JsonElement elem=input.get(obj.ParameterName);
						if (obj.IsDefault && elem==null) continue; 
						if (elem==null) throw new KProcedureException("Parameter "+obj.ParameterName+ " missing");
						if (obj.DataType.contains("CHAR")) {
							st.setString(obj.FixPosition,elem.getAsString());
						}else if (obj.DataType.contains("BIGINT")) {
							st.setLong(obj.FixPosition, elem.getAsLong());
						}else if (obj.DataType.contains("INT")) {
							st.setInt(obj.FixPosition, elem.getAsInt());	
						}else if (obj.DataType.contains("DECIMAL")) {
								st.setBigDecimal(obj.FixPosition, elem.getAsBigDecimal());	
						}else if (obj.DataType.contains("SECONDDATE")) {
							st.setDate(obj.FixPosition, new java.sql.Date(formatterSD.parse(elem.getAsString()).getTime()));							
						}else if (obj.DataType.contains("DATE")) {
							st.setDate(obj.FixPosition, new java.sql.Date(formatterDD.parse(elem.getAsString()).getTime()));							
						}else if (obj.DataType.contains("TIMESTAMP")) {
							st.setDate(obj.FixPosition, new java.sql.Date(formatterTS.parse(elem.getAsString()).getTime()));							
						}else {
							throw new KProcedureException("TYPE "+obj.DataType+ " not implemented");
						}												
					}
					else { 
						if (obj.DataType.contains("CHAR")) {
							st.registerOutParameter(obj.FixPosition, Types.NVARCHAR);
						}else if (obj.DataType.contains("INT")) {
							st.registerOutParameter(obj.FixPosition, Types.INTEGER);
						}else {
							throw new KProcedureException("TYPE "+obj.DataType+ " not implemented");
						}
					}
				}				
			}
			st.executeUpdate();		
			//Read Scalar out
			for (KMetaObj obj :parameters) { 						
				if (!obj.DataType.equals(table_type)) {
					if (obj.Type.equals("OUT") || obj.Type.equals("INOUT")) {
						if (obj.DataType.contains("CHAR")) {
							out.addProperty(obj.ParameterName,st.getString(obj.FixPosition));
						}else if (obj.DataType.contains("INT")) {
							out.addProperty(obj.ParameterName,st.getInt(obj.FixPosition));
						}else if (obj.DataType.contains("BIGINT")) {
							out.addProperty(obj.ParameterName,st.getLong(obj.FixPosition));							
						}else {
							throw new KProcedureException("TYPE "+obj.DataType+ " not implemented");
						}
					}
				}
			}
			//Read Tables out
			do {
				int resultCount=0;
				try (ResultSet rs = st.getResultSet();) {
					ResultSetMetaData mt = rs.getMetaData();
					String propName="ResultSet_"+resultCount++;
					for (KMetaObj obj :parameters) { 
						if (obj.DataType.equals(table_type)) {
							if (obj.Type.equals("OUT") || obj.Type.equals("INOUT")) {
								if (mt.getTableName(1).startsWith(obj.ParameterName)) {
									propName=obj.ParameterName;
									break;
								}
							}
						}
					}
					JsonArray jdata=new JsonArray();
					out.add(propName, jdata);						
					int numberOfColumns = mt.getColumnCount();
					while (rs.next()) {
						JsonObject jobj = new JsonObject();
						for (int i = 1; i <= numberOfColumns; i++) {	
							String ctype=mt.getColumnTypeName(i);
							if (ctype.contains("CHAR")) {
								jobj.addProperty(mt.getColumnLabel(i),rs.getString(i));
							}else if (ctype.contains("INT")) {
								jobj.addProperty(mt.getColumnLabel(i),rs.getInt(i));
							}else if (ctype.contains("BIGINT")) {
								jobj.addProperty(mt.getColumnLabel(i),rs.getLong(i));								
							}else if (ctype.contains("DECIMAL")) {
								jobj.addProperty(mt.getColumnLabel(i),rs.getBigDecimal(i));
							}else if (ctype.contains("TIMESTAMP")) {  
									jobj.addProperty(mt.getColumnLabel(i),formatterTS.format(rs.getDate(i)));								
							}else if (ctype.contains("DATE")) {  
								jobj.addProperty(mt.getColumnLabel(i),formatterDD.format(rs.getDate(i)));								
							}else if (ctype.contains("SECONDDATE")) {  
								jobj.addProperty(mt.getColumnLabel(i),formatterSD.format(rs.getDate(i)));								
							}else {
								throw new KProcedureException("TYPE "+ctype+ " not implemented");
							}
						}
						jdata.add(jobj);						
					}
				}
			} while (st.getMoreResults()); 		 
	    //Remove temp tables
		}finally {
			try (Statement stmt = pcon.createStatement();) {
				for (String droptable:tempDropTables) {
					stmt.addBatch(droptable);
				}
				stmt.executeBatch();
			}
		}   
		return out;		
	}

}
