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
            if (!File.Exists(jar))
            {
                throw new FileNotFoundException("JavaFX build is missing. Run build_launcher.ps1 first.");
            }

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

            runtimeJar = CreateRuntimeJar(jar);
            Directory.CreateDirectory(Path.GetDirectoryName(log));
            ProcessStartInfo start = new ProcessStartInfo();
            start.FileName = javaw;
            start.Arguments = "-jar " + Quote(runtimeJar);
            start.WorkingDirectory = root;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;

            using (StreamWriter writer = new StreamWriter(log, true))
            using (Process process = new Process())
            {
                object logLock = new object();
                writer.AutoFlush = true;
                writer.WriteLine("--- " + DateTime.Now.ToString("u") + " ---");
                process.StartInfo = start;
                process.OutputDataReceived += (sender, eventArgs) => {
                    if (eventArgs.Data != null) lock (logLock) writer.WriteLine(eventArgs.Data);
                };
                process.ErrorDataReceived += (sender, eventArgs) => {
                    if (eventArgs.Data != null) lock (logLock) writer.WriteLine(eventArgs.Data);
                };
                process.Start();
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                process.WaitForExit();
                if (process.ExitCode != 0)
                {
                    MessageBox.Show("JavaFX exited with code " + process.ExitCode
                            + ". See " + log, "Seeker Jobs", MessageBoxButtons.OK,
                            MessageBoxIcon.Error);
                }
                return process.ExitCode;
            }
        }
        catch (Exception error)
        {
            MessageBox.Show(error.Message, "Seeker Jobs", MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            return 1;
        }
        finally
        {
            if (runtimeJar != null) DeleteRuntimeJar(runtimeJar);
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
