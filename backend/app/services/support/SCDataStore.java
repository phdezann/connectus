package services.support;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

abstract public class SCDataStore extends AbstractDataStore<StoredCredential> {

    public SCDataStore(DataStoreFactory dataStoreFactory, String id) {
        super(dataStoreFactory, id);
    }

    @Override
    abstract public Set<String> keySet() throws IOException;

    @Override
    abstract public Collection<StoredCredential> values() throws IOException;

    @Override
    abstract public StoredCredential get(String key) throws IOException;

    @Override
    abstract public DataStore<StoredCredential> set(String key, StoredCredential value) throws IOException;

    @Override
    abstract public DataStore<StoredCredential> clear() throws IOException;

    @Override
    abstract public DataStore<StoredCredential> delete(String key) throws IOException;
}
