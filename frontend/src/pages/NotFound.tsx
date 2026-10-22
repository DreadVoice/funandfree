import { Link } from "react-router-dom";
import { Empty } from "../components/ui/Feedback";

export function NotFound() {
  return (
    <Empty title="Page not found">
      <Link to="/">Go back to the event list</Link>
    </Empty>
  );
}
