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
