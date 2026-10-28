Tests des règles Firestore (émulateur local) :
```bash
cd courant-douala/firebase
npm i firebase-tools@13.29.1 @firebase/rules-unit-testing@4.0.1 firebase@11.1.0
echo '{"firestore":{"rules":"firestore.rules"},"emulators":{"firestore":{"port":8085}}}' > firebase.json
npx firebase emulators:exec --only firestore --project demo-courant "node tests/rules.test.mjs"
```
