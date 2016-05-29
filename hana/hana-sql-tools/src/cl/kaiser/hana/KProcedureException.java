package cl.kaiser.hana;

public class KProcedureException extends Exception {
	
	private static final long serialVersionUID = 4354608826209653826L;

	public KProcedureException() { super(); }
	 
	public KProcedureException(String message) { super(message); }
	 
	public KProcedureException(String message, Throwable cause) { super(message, cause); }
	 
	public KProcedureException(Throwable cause) { super(cause); }	
	 
}
