package com.pessoal.agenda.infra.pairing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalNetworkAddressSelectorTest {
    @Test
    void physicalNetworkInterfacesHavePriorityOverVirtualOnes() {
        assertTrue(LocalNetworkAddressSelector.interfacePriority("wlp89s0")
                < LocalNetworkAddressSelector.interfacePriority("vmnet1"));
        assertTrue(LocalNetworkAddressSelector.interfacePriority("enp2s0")
                < LocalNetworkAddressSelector.interfacePriority("docker0"));
    }
}
