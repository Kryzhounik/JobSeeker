using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class SeekerLauncher
{
    [STAThread]
    private static int Main()
    {
        string directory = AppDomain.CurrentDomain.BaseDirectory;
        string jar = Path.Combine(directory, "dist", "seeker-gui-javafx.jar");
        string root = Path.GetFullPath(Path.Combine(directory, "..", ".."));
        string runId = DateTime.UtcNow.ToString("yyyyMMdd-HHmmss") + "-" + Guid.NewGuid().ToString("N");
        string log = Path.Combine(directory, "logs", "launch-" + runId + ".log");
        string runtimeJar = null;

        try
        {
            string javaHomeFile = Path.Combine(directory, "java-home.txt");
            string javaHome = File.Exists(javaHomeFile)
                    ? File.ReadAllText(javaHomeFile).Trim()
                    : Environment.GetEnvironmentVariable("JAVA_HOME");
            string javaw = Path.Combine(javaHome ?? "", "bin", "javaw.exe");
            if (!File.Exists(javaw))
            {
                throw new FileNotFoundException("Java runtime not found: " + javaw
                        + ". Run build_launcher.ps1 with a valid JDK 21 path.");
            }

            Directory.CreateDirectory(Path.GetDirectoryName(log));
            using (StreamWriter writer = new StreamWriter(log, true))
            {
                writer.AutoFlush = true;
                writer.WriteLine("--- " + DateTime.Now.ToString("u") + " ---");
                PrepareRuntime(directory, javaHome, writer);
                runtimeJar = CreateRuntimeJar(jar);
                ProcessStartInfo start = new ProcessStartInfo(javaw, "-jar " + Quote(runtimeJar));
                start.WorkingDirectory = root;
                int exitCode = RunLogged(start, writer);
                if (exitCode != 0)
                {
                    MessageBox.Show("JavaFX exited with code " + exitCode
                            + ". See " + log, "Seeker Jobs", MessageBoxButtons.OK,
                            MessageBoxIcon.Error);
                }
                return exitCode;
            }
        }
        catch (Exception error)
        {
            MessageBox.Show(error.Message + "\nSee " + log, "Seeker Jobs", MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            return 1;
        }
        finally
        {
            if (runtimeJar != null) DeleteRuntimeJar(runtimeJar);
        }
    }

    internal static void PrepareRuntime(string directory, string javaHome, TextWriter log)
    {
        ProcessStartInfo start = new ProcessStartInfo("powershell.exe",
                "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "
                + Quote(Path.Combine(directory, "prepare_runtime.ps1")) + " -JavaHome " + Quote(javaHome));
        start.WorkingDirectory = directory;
        if (RunLogged(start, log) != 0)
        {
            throw new InvalidOperationException("GUI preparation failed; the previous build was not launched.");
        }
    }

    private static int RunLogged(ProcessStartInfo start, TextWriter log)
    {
        start.UseShellExecute = false;
        start.CreateNoWindow = true;
        start.WindowStyle = ProcessWindowStyle.Hidden;
        start.RedirectStandardOutput = true;
        start.RedirectStandardError = true;
        object logLock = new object();
        using (Process process = new Process())
        {
            process.StartInfo = start;
            process.OutputDataReceived += (sender, data) => {
                if (data.Data != null) lock (logLock) log.WriteLine(data.Data);
            };
            process.ErrorDataReceived += (sender, data) => {
                if (data.Data != null) lock (logLock) log.WriteLine(data.Data);
            };
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            process.WaitForExit();
            return process.ExitCode;
        }
    }

    internal static string CreateRuntimeJar(string source)
    {
        string runDirectory = Path.Combine(Path.GetTempPath(), "SeekerJobs", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(runDirectory);
        string copy = Path.Combine(runDirectory, "seeker-gui-javafx.jar");
        try
        {
            // An atomic publish may rename the source while this handle reads the previous build.
            using (FileStream input = new FileStream(source, FileMode.Open, FileAccess.Read,
                    FileShare.ReadWrite | FileShare.Delete))
            using (FileStream output = new FileStream(copy, FileMode.CreateNew, FileAccess.Write))
            {
                input.CopyTo(output);
            }
            return copy;
        }
        catch
        {
            DeleteRuntimeJar(copy);
            throw;
        }
    }

    internal static void DeleteRuntimeJar(string jar)
    {
        try
        {
            File.Delete(jar);
            Directory.Delete(Path.GetDirectoryName(jar));
        }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
    }

    private static string Quote(string value)
    {
        return "\"" + value.Replace("\"", "\\\"") + "\"";
    }
}
