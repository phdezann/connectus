package services.support;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;

import java.io.IOException;

@SuppressWarnings("unchecked")
abstract public class SCRepositoryDataStoreFactory extends AbstractDataStoreFactory {

    @Override
    protected DataStore<StoredCredential> createDataStore(String id) throws IOException {
        return createStoredCredentialDataStore(id);
    }

    abstract protected DataStore<StoredCredential> createStoredCredentialDataStore(String id);
}
