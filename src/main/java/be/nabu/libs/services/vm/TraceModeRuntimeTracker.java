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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceRuntimeTracker;
import be.nabu.libs.services.vm.api.Step;

public class TraceModeRuntimeTracker implements ServiceRuntimeTracker {

	public static final String TRACE_TIMEOUT = "be.nabu.services.traceTimeout";
	
	private List<String> breakpoints = new ArrayList<String>();
	
	public TraceModeRuntimeTracker(String...breakpoints) {
		this.breakpoints.addAll(Arrays.asList(breakpoints));
	}
	
	@Override
	public void start(Service service) {
		// do nothing
	}

	@Override
	public void stop(Service service) {
		// do nothing
	}

	@Override
	public void error(Service service, Exception exception) {
		// do nothing
	}

	@Override
	public void before(Object step) {
		if (step instanceof Step) {
			if (breakpoints.contains(((Step) step).getId())) {
				try {
					Thread.sleep(Long.parseLong(System.getProperty(TRACE_TIMEOUT, "180000")));
					// if not interrupted, abort execution
					if (ServiceRuntime.getRuntime() != null) {
						ServiceRuntime.getRuntime().abort();
					}
				}
				catch (InterruptedException e) {
					// continue
				}
			}
		}
	}

	@Override
	public void after(Object step) {
		// do nothing
	}

	@Override
	public void error(Object step, Exception exception) {
		// do nothing
	}

	@Override
	public void report(Object object) {
		// do nothing
	}
}
