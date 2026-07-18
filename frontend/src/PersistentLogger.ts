type LogLevel = "log" | "warn" | "error" | "info" | "debug";

export class PersistentLogger {
    static log(level: LogLevel, ...args: any[]) {
        // Print to console as usual
        console[level](...args);

        // Store in localStorage
        const entry = {
            level,
            message: args.map(String).join(" "),
            timestamp: new Date().toISOString(),
        };

        const logs = JSON.parse(localStorage.getItem("logs") || "[]");
        logs.push(entry);
        localStorage.setItem("logs", JSON.stringify(logs.slice(-500))); // keep last 500 logs
    }

    static getLogs() {
        return JSON.parse(localStorage.getItem("logs") || "[]");
    }

    static clear() {
        localStorage.removeItem("logs");
    }

    static logWithContext(context: string, level: LogLevel, ...args: any[]) {
        const msg = `[${context}] ${args.map(String).join(" ")}`;
        PersistentLogger.log(level, msg);
    }
}
