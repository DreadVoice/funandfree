import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { Button } from "./ui/Button";
import { ThemeToggle } from "./ui/ThemeToggle";

export function Layout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  const navClass = ({ isActive }: { isActive: boolean }) =>
    isActive ? "navlink navlink-active" : "navlink";

  return (
    <>
      <header className="appbar">
        <div className="container appbar-inner">
          <Link to="/" className="brand">
            Events
          </Link>
          <NavLink to="/" className={navClass} end>
            Discover
          </NavLink>
          {user ? (
            <>
              <NavLink to="/bookings" className={navClass}>
                My bookings
              </NavLink>
              <NavLink to="/organizer" className={navClass}>
                Organize
              </NavLink>
            </>
          ) : null}
          <span style={{ flex: 1 }} />
          <ThemeToggle />
          {user ? (
            <Button
              variant="outline"
              size="sm"
              onClick={async () => {
                await signOut();
                navigate("/");
              }}
            >
              Sign out
            </Button>
          ) : (
            <Button size="sm" onClick={() => navigate("/login")}>
              Sign in
            </Button>
          )}
        </div>
      </header>
      <main className="container page">
        <Outlet />
      </main>
    </>
  );
}
