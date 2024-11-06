/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.services.vm;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.cluster.api.ClusterInstance;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.vm.ManagedCloseable.Scope;

public class VMContext {

	/**
	 * This is used to determine the break count at any given time
	 * Any control structure must check if it is over 0 and break if necessary (and decrease)
	 */
	private int breakCount = 0;
	// when you break, do you want to continue execution, this is mostly helpful when done in a for loop?
	private boolean continueExecution;
	
	private ExecutionContext executionContext;
	
	private Exception caughtException;
	
	private List<Closeable> mapCloseables = null,
			serviceCloseables = new ArrayList<Closeable>();
	
	private VMServiceInstance serviceInstance;
	
	private ClusterInstance cluster;
	
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

	public boolean isContinueExecution() {
		return continueExecution;
	}

	public void setContinueExecution(boolean continueExecution) {
		this.continueExecution = continueExecution;
	}

	public boolean mustBreak() {
		return breakCount > 0;
	}
	
	public int decreaseBreakCount() {
		this.breakCount--;
		if (this.breakCount < 0)
			throw new RuntimeException("The break counter went beneath 0, this should not occur");
		return breakCount;
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

	public ClusterInstance getCluster() {
		return cluster;
	}

	public void setCluster(ClusterInstance cluster) {
		this.cluster = cluster;
	}
}
