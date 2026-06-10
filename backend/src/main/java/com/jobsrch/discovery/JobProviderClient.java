package com.jobsrch.discovery;

import java.util.List;

/**
 * Adapts one public job-board API to JobSrch's provider-neutral result model.
 *
 * <p>Implementations own provider-specific HTTP and JSON details. Filtering,
 * result limits, and persistence decisions remain outside the adapter.</p>
 */
public interface JobProviderClient {

    JobProvider provider();

    List<DiscoveredJob> fetch(String companyIdentifier, String companyName);
}
