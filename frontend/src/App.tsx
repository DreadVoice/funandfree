import { RouterProvider } from "react-router-dom";
import { AuthProvider } from "./lib/auth";
import { router } from "./router";

export function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  );
}
