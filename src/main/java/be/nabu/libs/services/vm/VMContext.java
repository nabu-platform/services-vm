package be.nabu.libs.services.vm;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.vm.ManagedCloseable.Scope;

public class VMContext {

	/**
	 * This is used to determine the break count at any given time
	 * Any control structure must check if it is over 0 and break if necessary (and decrease)
	 */
	private int breakCount = 0;
	
	private ExecutionContext executionContext;
	
	private Exception caughtException;
	
	private List<Closeable> mapCloseables = null,
			serviceCloseables = new ArrayList<Closeable>();
	
	private VMServiceInstance serviceInstance;
	
	public VMContext(ExecutionContext executionContext, VMServiceInstance serviceInstance) {
		this.executionContext = executionContext;
		this.serviceInstance = serviceInstance;
	}
	
	public VMServiceInstance getServiceInstance() {
		return serviceInstance;
	}
	
	/** Managed closeables */
	
	public void newMapStep() {
		mapCloseables = new ArrayList<Closeable>();
	}
	
	public List<Closeable> getManaged(Scope scope) {
		if (scope == Scope.MAP)
			return mapCloseables;
		else
			return serviceCloseables;
	}
	
	public void addManaged(Closeable closeable, Scope scope) {
		switch(scope) {
			case MAP: 
				mapCloseables.add(closeable);
			break;
			default:
				serviceCloseables.add(closeable);
		}
	}
	public void addManaged(List<Closeable> closeables, Scope scope) {
		switch(scope) {
			case MAP: 
				mapCloseables.addAll(closeables);
			break;
			default:
				serviceCloseables.addAll(closeables);
		}
	}
	
	public void setBreakCount(int breakCount) {
		this.breakCount = breakCount;
	}
	
	public int getBreakCount() {
		return breakCount;
	}
	
	public boolean mustBreak() {
		return breakCount > 0;
	}
	
	public void decreaseBreakCount() {
		this.breakCount--;
		if (this.breakCount < 0)
			throw new RuntimeException("The break counter went beneath 0, this should not occur");
	}
	
	public ExecutionContext getExecutionContext() {
		return executionContext;
	}

	public Exception getCaughtException() {
		return caughtException;
	}

	public void setCaughtException(Exception caughtException) {
		this.caughtException = caughtException;
	}
}
