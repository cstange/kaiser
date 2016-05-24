package cl.kaiser.odata;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import com.google.gson.stream.JsonWriter;

import cl.kaiser.odata.gen.OdataFilterLexer;
import cl.kaiser.odata.gen.OdataFilterParser;

public class OdataImpSql {

	public void QueryJsonObject(
			Connection con,
			OutputStream stream,
			String callback,
			String select,
			String object,
			String filter,
			String orderby,
			long top, 
			long skip, 
			boolean inlinecount //count first
	) throws SQLException, IOException 
	{
		
		String query="";
		String cols="*";	
		String wfilter="";
		if (select!=null) select=cols;
		if (filter!=null) {
			ANTLRInputStream input = new ANTLRInputStream(filter);
			OdataFilterLexer lexer = new OdataFilterLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			OdataFilterParser parser = new OdataFilterParser(tokens);
			ParseTree tree = parser.prog();
			OdataFilterSql eval = new OdataFilterSql();	
			wfilter=eval.visit(tree);		
		}
		query="select "+cols+" from "+object+" "+wfilter;
		if (orderby!=null) query+=" order by "+orderby;
		if (top>0) query+=" limit "+top;
		if (skip>0) query+=" offset "+skip;				
		long duration=System.currentTimeMillis();				
		
		try (OutputStreamWriter out= new OutputStreamWriter(stream, "UTF-8"); JsonWriter writer = new JsonWriter(out)) {
			if (callback!=null) { out.write(callback+"("); out.flush(); }
			writer.beginObject();
			writer.name("d");
			writer.beginObject();
			if (inlinecount) {
				long records=0;
				String queryInline="select count(1) from "+object+" "+wfilter;
				try (PreparedStatement stm=con.prepareStatement(queryInline)) {
					//TODO: Pendiente ?,?,? en stament
					try (ResultSet rs=stm.executeQuery()) {			
						if(rs.next()) {
							records=rs.getLong(1);
						}
					}
				}
				writer.name("__count").value(records);
			}
			try (PreparedStatement stm=con.prepareStatement(query)) {
				//TODO: Pendiente ?,?,? en stament
				try (ResultSet rs=stm.executeQuery()) {				
						ResultSetMetaData rsmd = rs.getMetaData();
						writer.name("results");
						writer.beginArray();
						long record=0;
						while(rs.next()) {
						   writer.beginObject();
						   for(int idx=1; idx<=rsmd.getColumnCount(); idx++) {
							    writer.name(rsmd.getColumnLabel(idx)); 
						     	if(rsmd.getColumnType(idx)==java.sql.Types.BIGINT){
						     		writer.value(rs.getLong(idx)+"");
						        }
						        else if(rsmd.getColumnType(idx)==java.sql.Types.BOOLEAN){
						        	writer.value(rs.getBoolean(idx));						        
						        }
						        else if(rsmd.getColumnType(idx)==java.sql.Types.DOUBLE){
						        	writer.value(rs.getDouble(idx));						        
						        }			
						        else if(rsmd.getColumnType(idx)==java.sql.Types.FLOAT){
						        	writer.value(rs.getFloat(idx));						        
						        }							     	
						        else if(rsmd.getColumnType(idx)==java.sql.Types.INTEGER){
						        	writer.value(rs.getInt(idx));						        
						        }	
						        else if(rsmd.getColumnType(idx)==java.sql.Types.NVARCHAR){
						        	writer.value(rs.getNString(idx));						        
						        }						     	
						        else if(rsmd.getColumnType(idx)==java.sql.Types.VARCHAR){
						        	writer.value(rs.getString(idx));						        
						        }	
						        else if(rsmd.getColumnType(idx)==java.sql.Types.TINYINT){
						        	writer.value(rs.getInt(idx));						        
						        }					
						        else if(rsmd.getColumnType(idx)==java.sql.Types.SMALLINT){
						        	writer.value(rs.getInt(idx));						        
						        }	
						        else if(rsmd.getColumnType(idx)==java.sql.Types.DATE){
						        	if (rs.getDate(idx)!=null)
						        		writer.value("/Date("+rs.getDate(idx).getTime()+")/" );
						        	else 
						        		writer.value("/Date(-62135596800000)/" );							        	
						        }	
						        else if(rsmd.getColumnType(idx)==java.sql.Types.TIMESTAMP){
						        	if (rs.getTimestamp(idx)!=null)
						        		writer.value("/Date("+rs.getTimestamp(idx).getTime()+")/" );
						        	else 
						        		writer.value("/Date(-62135596800000)/" );	
						        }							     	
						        else{
						        	writer.value(rs.getString(idx));
						        }						     							   
						   }					   
						   writer.endObject();//end record						   
						   if (record++%100==0) writer.flush();
						}
						writer.endArray();//end results
					
				}			
			}
			duration=System.currentTimeMillis()-duration;	
			writer.name("duration").value(duration);
			writer.endObject();
			writer.endObject();
			writer.flush();
			if (callback!=null) { out.write(")"); out.flush(); }			
		}

	}
}
