package com.jobsrch.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderSupportTests {

    @Test
    void infersUnitedStatesFromCountryStateNameAndStateCode() {
        assertThat(ProviderSupport.inferCountryCode("United States")).isEqualTo("US");
        assertThat(ProviderSupport.inferCountryCode("New York, New York")).isEqualTo("US");
        assertThat(ProviderSupport.inferCountryCode("Austin, TX")).isEqualTo("US");
    }

    @Test
    void preservesKnownNonUsCountriesAndUnknownLocations() {
        assertThat(ProviderSupport.inferCountryCode("Tokyo, Japan")).isEqualTo("JP");
        assertThat(ProviderSupport.inferCountryCode("Sydney, Australia")).isEqualTo("AU");
        assertThat(ProviderSupport.inferCountryCode("Remote, Brazil")).isEqualTo("BR");
        assertThat(ProviderSupport.inferCountryCode("Amsterdam")).isEqualTo("NL");
        assertThat(ProviderSupport.inferCountryCode("Bengaluru")).isEqualTo("IN");
        assertThat(ProviderSupport.inferCountryCode("Dublin")).isEqualTo("IE");
        assertThat(ProviderSupport.inferCountryCode("Toronto")).isEqualTo("CA");
        assertThat(ProviderSupport.inferCountryCode("Remote")).isNull();
        assertThat(ProviderSupport.inferCountryCode("In-Office")).isNull();
        assertThat(ProviderSupport.inferCountryCode(
                "Singapore; San Francisco, California")).isNull();
    }

    @Test
    void usesWordBoundariesAndRetainsMixedCountryEvidence() {
        assertThat(ProviderSupport.inferCountryCode("Indiana")).isEqualTo("US");
        assertThat(ProviderSupport.inferCountryCode("Albuquerque, New Mexico"))
                .isEqualTo("US");
        assertThat(ProviderSupport.inferCountryCode("Atlanta, Georgia"))
                .isEqualTo("US");
        assertThat(ProviderSupport.inferCountryCode("Tbilisi, Georgia"))
                .isEqualTo("GE");
        assertThat(ProviderSupport.inferCountryCodes(
                "San Francisco, California; Vancouver, Canada"))
                .containsExactlyInAnyOrder("US", "CA");
    }

    @Test
    void normalizesCommonWorkplaceDescriptions() {
        assertThat(ProviderSupport.inferWorkplaceType("Remote - United States"))
                .isEqualTo(WorkplaceType.REMOTE);
        assertThat(ProviderSupport.inferWorkplaceType("Hybrid role in Chicago"))
                .isEqualTo(WorkplaceType.HYBRID);
        assertThat(ProviderSupport.inferWorkplaceType("On-site in Seattle"))
                .isEqualTo(WorkplaceType.ON_SITE);
        assertThat(ProviderSupport.inferWorkplaceType("In-Office"))
                .isEqualTo(WorkplaceType.ON_SITE);
    }

    @Test
    void infersCountryFromUnambiguousPostingText() {
        assertThat(ProviderSupport.inferCountryCode("Available location: Singapore"))
                .isEqualTo("SG");
    }
}
