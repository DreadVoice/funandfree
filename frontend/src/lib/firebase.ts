import { initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
};

export const firebaseConfigured = Boolean(config.apiKey && config.authDomain && config.projectId);

let app: FirebaseApp | null = null;
let auth: Auth | null = null;

export function getFirebaseAuth(): Auth {
  if (!firebaseConfigured) {
    throw new Error(
      "Firebase is not configured. Copy frontend/.env.example to frontend/.env and fill in the VITE_FIREBASE_* values.",
    );
  }
  if (!app) {
    app = initializeApp(config);
  }
  if (!auth) {
    auth = getAuth(app);
  }
  return auth;
}
