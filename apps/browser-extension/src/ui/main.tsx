import { render } from "preact";
import { App } from "./App.tsx";
import "./theme.css";

render(<App />, document.getElementById("root") as HTMLElement);
