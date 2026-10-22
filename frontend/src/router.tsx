import { createBrowserRouter } from "react-router-dom";
import { Layout } from "./components/Layout";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { EventDetail } from "./pages/EventDetail";
import { EventList } from "./pages/EventList";
import { Login } from "./pages/Login";
import { MyBookings } from "./pages/MyBookings";
import { NotFound } from "./pages/NotFound";
import { Dashboard } from "./pages/organizer/Dashboard";
import { EventForm } from "./pages/organizer/EventForm";

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: "/", element: <EventList /> },
      { path: "/events/:eventId", element: <EventDetail /> },
      { path: "/login", element: <Login /> },
      {
        element: <ProtectedRoute />,
        children: [
          { path: "/bookings", element: <MyBookings /> },
          { path: "/organizer", element: <Dashboard /> },
          { path: "/organizer/events/new", element: <EventForm /> },
          { path: "/organizer/events/:eventId", element: <EventForm /> },
        ],
      },
      { path: "*", element: <NotFound /> },
    ],
  },
]);
