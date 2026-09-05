package com.pessoal.agenda.infra.pairing;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Comparator;
import java.util.Locale;

public final class LocalNetworkAddressSelector {
    private LocalNetworkAddressSelector() {}

    public static InetAddress select() {
        try {
            return NetworkInterface.networkInterfaces()
                    .filter(LocalNetworkAddressSelector::usableInterface)
                    .flatMap(network -> network.inetAddresses()
                            .filter(address -> address instanceof Inet4Address)
                            .filter(InetAddress::isSiteLocalAddress)
                            .map(address -> new Candidate(network.getName(), address)))
                    .sorted(Comparator.comparingInt((Candidate candidate) ->
                                    interfacePriority(candidate.interfaceName()))
                            .thenComparing(candidate -> candidate.address().getHostAddress()))
                    .map(Candidate::address)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Nenhum endereço IPv4 de rede local está disponível."));
        } catch (SocketException error) {
            throw new IllegalStateException("Não foi possível consultar a rede local.", error);
        }
    }

    private static boolean usableInterface(NetworkInterface network) {
        try {
            return network.isUp() && !network.isLoopback() && !network.isVirtual();
        } catch (SocketException error) {
            return false;
        }
    }

    static int interfacePriority(String interfaceName) {
        String name = interfaceName.toLowerCase(Locale.ROOT);
        if (name.startsWith("wl") || name.startsWith("wlan")) return 0;
        if (name.startsWith("en") || name.startsWith("eth")) return 1;
        if (name.startsWith("vmnet") || name.startsWith("veth")
                || name.startsWith("docker") || name.startsWith("virbr")
                || name.startsWith("br-")) return 3;
        return 2;
    }

    private record Candidate(String interfaceName, InetAddress address) {}
}
