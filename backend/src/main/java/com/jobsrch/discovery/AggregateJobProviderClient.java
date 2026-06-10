package com.jobsrch.discovery;

import java.util.List;

public interface AggregateJobProviderClient {

    JobProvider provider();

    boolean enabled();

    List<DiscoveredJob> search(String query, String location, Integer postedWithinDays);
}
