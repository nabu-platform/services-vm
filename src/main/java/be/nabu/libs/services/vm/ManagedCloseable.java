package be.nabu.libs.services.vm;

public class ManagedCloseable {
	
	public enum Scope {
		MAP, SERVICE
	}
	
	private String query;
	
	private Scope scope;

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public Scope getScope() {
		return scope;
	}

	public void setScope(Scope scope) {
		this.scope = scope;
	}
}
