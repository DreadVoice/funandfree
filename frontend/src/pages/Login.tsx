import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "../components/ui/Button";
import { Field, TextInput } from "../components/ui/Field";
import { Alert } from "../components/ui/Feedback";
import { describeAuthError, useAuth } from "../lib/auth";

export function Login() {
  const { signIn, signUp, configured } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [mode, setMode] = useState<"signIn" | "signUp">("signIn");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!configured) {
    return (
      <div className="stack" style={{ maxWidth: 520 }}>
        <h1>Firebase is not configured</h1>
        <Alert tone="danger">
          Copy <code>frontend/.env.example</code> to <code>frontend/.env</code> and fill in
          VITE_FIREBASE_API_KEY, VITE_FIREBASE_AUTH_DOMAIN and VITE_FIREBASE_PROJECT_ID from the
          Firebase console, then restart the dev server.
        </Alert>
      </div>
    );
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      if (mode === "signIn") {
        await signIn(email, password);
      } else {
        await signUp(email, password);
      }
      navigate(from, { replace: true });
    } catch (caught) {
      setError(describeAuthError(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack" style={{ maxWidth: 420, margin: "32px auto" }}>
      <div className="stack" style={{ gap: 4 }}>
        <h1>{mode === "signIn" ? "Sign in" : "Create an account"}</h1>
        <p className="muted small">
          {mode === "signIn"
            ? "Use the email and password you registered with."
            : "New accounts start as attendees."}
        </p>
      </div>

      <form
        className="card stack"
        onSubmit={(formEvent) => {
          formEvent.preventDefault();
          void submit();
        }}
      >
        {error ? <Alert tone="danger">{error}</Alert> : null}

        <Field label="Email">
          <TextInput
            type="email"
            value={email}
            autoComplete="email"
            required
            onChange={(inputEvent) => setEmail(inputEvent.target.value)}
          />
        </Field>

        <Field label="Password" hint={mode === "signUp" ? "At least six characters." : undefined}>
          <TextInput
            type="password"
            value={password}
            autoComplete={mode === "signIn" ? "current-password" : "new-password"}
            required
            onChange={(inputEvent) => setPassword(inputEvent.target.value)}
          />
        </Field>

        <Button type="submit" disabled={busy}>
          {busy ? "Working..." : mode === "signIn" ? "Sign in" : "Create account"}
        </Button>

        <Button
          variant="text"
          size="sm"
          onClick={() => {
            setMode(mode === "signIn" ? "signUp" : "signIn");
            setError(null);
          }}
        >
          {mode === "signIn" ? "Need an account? Sign up" : "Already registered? Sign in"}
        </Button>
      </form>
    </div>
  );
}
