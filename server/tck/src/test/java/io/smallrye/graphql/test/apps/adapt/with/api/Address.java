package io.smallrye.graphql.test.apps.adapt.with.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Address
 *
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class Address {

    public AddressType addressType;
    public List<String> lines = new ArrayList<>();

    public void addLine(String line) {
        this.lines.add(line);
    }

    @Override
    public String toString() {
        return "Address{" + "addressType=" + addressType + ", lines=" + lines + '}';
    }

}
