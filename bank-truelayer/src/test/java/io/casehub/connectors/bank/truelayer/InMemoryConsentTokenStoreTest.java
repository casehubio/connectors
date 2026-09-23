package io.casehub.connectors.bank.truelayer;

class InMemoryConsentTokenStoreTest extends ConsentTokenStoreContractTest {

    @Override
    protected ConsentTokenStore createStore() {
        return new InMemoryConsentTokenStore();
    }
}
