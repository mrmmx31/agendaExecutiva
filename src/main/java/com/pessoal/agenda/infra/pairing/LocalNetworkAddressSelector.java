package com.pessoal.agenda.infra.pairing;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Comparator;

public final class LocalNetworkAddressSelector {
    private LocalNetworkAddressSelector() {}

    public static InetAddress select() {
        try {
            return NetworkInterface.networkInterfaces()
                    .filter(LocalNetworkAddressSelector::usableInterface)
                    .flatMap(NetworkInterface::inetAddresses)
                    .filter(address -> address instanceof Inet4Address)
                    .filter(InetAddress::isSiteLocalAddress)
                    .sorted(Comparator.comparing(InetAddress::getHostAddress))
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
}
