package dev.hackercode.execution;

import dev.hackercode.common.ApiException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Supported languages with everything each execution backend needs. Judge0 ids refer to Judge0 CE defaults and
 * can be overridden with JUDGE0_LANGUAGE_IDS if a deployment uses different ones.
 */
public enum Language {
    C("c", "C", "main.c", "c", 50, "c", "gcc:14",
            "gcc -O2 -std=c17 -o /tmp/main /code/main.c -lm", "/tmp/main",
            """
            #include <stdio.h>

            int main(void) {
                int n;
                if (scanf("%d", &n) != 1) {
                    printf("Hello from HackerCode!\\n");
                    return 0;
                }
                printf("You entered %d\\n", n);
                return 0;
            }
            """),
    CPP("cpp", "C++", "main.cpp", "cpp", 54, "c++", "gcc:14",
            "g++ -O2 -std=c++17 -o /tmp/main /code/main.cpp", "/tmp/main",
            """
            #include <bits/stdc++.h>
            using namespace std;

            int main() {
                ios::sync_with_stdio(false);
                cin.tie(nullptr);
                int n;
                if (!(cin >> n)) {
                    cout << "Hello from HackerCode!" << '\\n';
                    return 0;
                }
                cout << "You entered " << n << '\\n';
                return 0;
            }
            """),
    JAVA("java", "Java", "Main.java", "java", 62, "java", "eclipse-temurin:21-jdk",
            "javac -d /tmp /code/Main.java", "java -Xss64m -cp /tmp Main",
            """
            import java.io.*;
            import java.util.*;

            public class Main {
                public static void main(String[] args) throws IOException {
                    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                    String line = in.readLine();
                    if (line == null || line.isBlank()) {
                        System.out.println("Hello from HackerCode!");
                        return;
                    }
                    int n = Integer.parseInt(line.trim());
                    System.out.println("You entered " + n);
                }
            }
            """),
    PYTHON("python", "Python", "main.py", "python", 71, "python", "python:3.12-alpine",
            null, "python3 /code/main.py",
            """
            import sys


            def main() -> None:
                data = sys.stdin.read().split()
                if not data:
                    print("Hello from HackerCode!")
                    return
                n = int(data[0])
                print(f"You entered {n}")


            if __name__ == "__main__":
                main()
            """),
    JAVASCRIPT("javascript", "JavaScript", "main.js", "javascript", 63, "javascript", "node:22-alpine",
            null, "node /code/main.js",
            """
            const input = require("fs").readFileSync(0, "utf8").trim();

            function main() {
              if (!input) {
                console.log("Hello from HackerCode!");
                return;
              }
              const n = Number(input.split(/\\s+/)[0]);
              console.log(`You entered ${n}`);
            }

            main();
            """),
    GO("go", "Go", "main.go", "go", 60, "go", "golang:1.23-alpine",
            "cd /tmp && cp /code/main.go . && go build -o /tmp/main main.go", "/tmp/main",
            """
            package main

            import (
            	"bufio"
            	"fmt"
            	"os"
            )

            func main() {
            	reader := bufio.NewReader(os.Stdin)
            	var n int
            	if _, err := fmt.Fscan(reader, &n); err != nil {
            		fmt.Println("Hello from HackerCode!")
            		return
            	}
            	fmt.Printf("You entered %d\\n", n)
            }
            """);

    private final String id;
    private final String label;
    private final String fileName;
    private final String monacoId;
    private final int judge0Id;
    private final String pistonName;
    private final String dockerImage;
    private final String compileCommand;
    private final String runCommand;
    private final String starter;

    Language(String id, String label, String fileName, String monacoId, int judge0Id, String pistonName, String dockerImage,
             String compileCommand, String runCommand, String starter) {
        this.id = id;
        this.label = label;
        this.fileName = fileName;
        this.monacoId = monacoId;
        this.judge0Id = judge0Id;
        this.pistonName = pistonName;
        this.dockerImage = dockerImage;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
        this.starter = starter;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String fileName() { return fileName; }
    public String monacoId() { return monacoId; }
    public int judge0Id() { return judge0Id; }
    public String pistonName() { return pistonName; }
    public String dockerImage() { return dockerImage; }
    public String compileCommand() { return compileCommand; }
    public String runCommand() { return runCommand; }
    public String starter() { return starter; }
    public boolean compiled() { return compileCommand != null; }

    public static Language require(String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(l -> l.id.equals(key)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("unsupported_language", "Unsupported language: " + id));
    }
}
