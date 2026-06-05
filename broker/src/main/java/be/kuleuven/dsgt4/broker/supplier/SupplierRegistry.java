package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.data.SupplierType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*
   Routes a call to the right supplier by type: a caller does suppliers.get(type) to obtain
   that supplier's client, then invokes the operation on it.
*/
@Component
public class SupplierRegistry {
    private final Map<SupplierType, SupplierClient> clients = new EnumMap<>(SupplierType.class);

    public SupplierRegistry(List<SupplierClient> allClients) {
        for (SupplierClient client : allClients) {
            clients.put(client.type(), client);
        }
    }

    public SupplierClient get(SupplierType type) {
        SupplierClient client = clients.get(type);
        if (client == null) {
            throw new IllegalStateException("no supplier client registered for " + type);
        }
        return client;
    }
}
