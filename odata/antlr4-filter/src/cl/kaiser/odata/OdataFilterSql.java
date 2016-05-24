package cl.kaiser.odata;

import cl.kaiser.odata.gen.OdataFilterBaseVisitor;
import cl.kaiser.odata.gen.OdataFilterParser;

import java.util.HashMap;
import java.util.Map;

public class OdataFilterSql extends OdataFilterBaseVisitor<String> {
	Map<String, Double> memory = new HashMap<String, Double>();
	
	@Override
	public String visitProg(OdataFilterParser.ProgContext ctx) {
		String val="where "+visit(ctx.boolexpr()); 
		if (val.equals("where null")) return "";
		return val;
	}
	
	@Override 
	public String visitGroup(OdataFilterParser.GroupContext ctx) { 
		String val=" ( "+visit(ctx.boolexpr())+ " ) "; 
		return val;
	}
	
	@Override public String visitOperatorAnd(OdataFilterParser.OperatorAndContext ctx) { 
		String val=" ( "+visit(ctx.boolexpr(0))+ " AND " + visit(ctx.boolexpr(1)) + " ) "; 
		return val;	
	}
	
	@Override 
	public String visitOperatorOr(OdataFilterParser.OperatorOrContext ctx) { 
		String val=" ( "+visit(ctx.boolexpr(0))+ " OR " + visit(ctx.boolexpr(1)) + " ) "; 
		return val;	
	}
	
	@Override 
	public String visitOperatorBool(OdataFilterParser.OperatorBoolContext ctx) { 		
		String val=" ( "+visit(ctx.condboolexpr())+ " ) "; 
		return val;		
	}
	

	@Override 
	public String visitNe(OdataFilterParser.NeContext ctx) { 
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();				
		String val=left + " <> " +right; 
		return val;		
	}
	
	@Override 
	public String visitQe(OdataFilterParser.QeContext ctx) { 
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();				
		String val=left + " >= " +right; 
		return val;		
	}
	
	@Override 
	public String visitQt(OdataFilterParser.QtContext ctx) { 
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();				
		String val=left + " > " +right; 
		return val;		
	}
	
	
	@Override 
	public String visitLe(OdataFilterParser.LeContext ctx) { 
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();				
		String val=left + " <= " +right; 
		return val;		
	}	
	
	@Override 
	public String visitLt(OdataFilterParser.LtContext ctx) { 
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();				
		String val=left + " < " +right; 
		return val;		
	}	
	
	@Override 
	public String visitEq(OdataFilterParser.EqContext ctx) {
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();				
		String val=left + " = " +right; 
		return val;				
	}
	
	@Override 
	public String visitEqNull(OdataFilterParser.EqNullContext ctx) {
		String left=ctx.condleftexpr().getText();			
		String val=left + " is null "; 
		return val;				
	}	
	
	@Override 
	public String visitNqNull(OdataFilterParser.NqNullContext ctx) {
		String left=ctx.condleftexpr().getText();			
		String val=left + " is not null "; 
		return val;				
	}	
	
	@Override 
	public String visitEqEmpty(OdataFilterParser.EqEmptyContext ctx) {
		String left=ctx.condleftexpr().getText();			
		String val=left + " =  '' "; 
		return val;				
	}		
	
	@Override 
	public String visitNqEmpty(OdataFilterParser.NqEmptyContext ctx) {
		String left=ctx.condleftexpr().getText();			
		String val=left + " <>  '' "; 
		return val;				
	}		
		
	@Override 
	public String visitStartswith(OdataFilterParser.StartswithContext ctx) {
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();	
		String val=" LOCATE ("+left+", "+right+") = 1 ";
		if (!right.startsWith("'") && left.startsWith("'")) 
			val=" "+right+" like '"+left.substring(1,left.length()-1)+"%' ";
		return val;				
	}
	
	@Override 
	public String visitEndswith(OdataFilterParser.EndswithContext ctx) {
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();	
		String val=" LOCATE ("+left+", "+right+", -1) = 1 ";	
		if (!right.startsWith("'") && left.startsWith("'")) 
			val=" "+right+" like '%"+left.substring(1,left.length()-1)+"' ";		
		return val;				
	}	
	
	@Override 
	public String visitSubstringof(OdataFilterParser.SubstringofContext ctx) {
		String left=ctx.condleftexpr().getText();
		String right=ctx.condrightexpr().getText();	
		String val=" LOCATE ("+left+", "+right+") > 0 ";	
		if (!right.startsWith("'") && left.startsWith("'")) 
			val=" "+right+" like '%"+left.substring(1,left.length()-1)+"%' ";			
		return val;				
	}	
	
}
