using System;
using System.Diagnostics;
using System.IO;

internal static class LauncherSnapshotSmoke
{
    private static int Main(string[] args)
    {
        string fixture = Path.Combine(Path.GetTempPath(), "SeekerLauncherSmoke-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(fixture);
        string published = Path.Combine(fixture, "published.jar");
        string next = Path.Combine(fixture, "next.jar");
        string first = null;
        string second = null;
        try
        {
            File.WriteAllText(next, "first build");
            Publish(args[0], next, published, true);
            first = SeekerLauncher.CreateRuntimeJar(published);
            using (FileStream reader = new FileStream(published, FileMode.Open, FileAccess.Read,
                    FileShare.ReadWrite | FileShare.Delete))
            {
                File.WriteAllText(next, "second build with different bytes");
                Publish(args[0], next, published, true);
                using (StreamReader text = new StreamReader(reader))
                {
                    Check(text.ReadToEnd() == "first build", "Publish changed an already-open archive");
                }
            }
            second = SeekerLauncher.CreateRuntimeJar(published);
            Check(first != second, "Launches shared a runtime path");
            Check(File.ReadAllText(first) == "first build", "Old launch changed after publish");
            Check(File.ReadAllText(second) == "second build with different bytes", "New launch missed the new build");
            Publish(args[0], Path.Combine(fixture, "missing.jar"), published, false);
            Check(File.ReadAllText(published) == File.ReadAllText(second), "Failed publish damaged the last good build");
            Check(Directory.GetFiles(fixture, "*.tmp").Length == 0, "Publish left a staging file");
            SeekerLauncher.DeleteRuntimeJar(first);
            SeekerLauncher.DeleteRuntimeJar(second);
            Check(!Directory.Exists(Path.GetDirectoryName(first)), "First runtime copy was not removed");
            Check(!Directory.Exists(Path.GetDirectoryName(second)), "Second runtime copy was not removed");
            Console.WriteLine("Launcher snapshot isolation, atomic publish, failed publish and cleanup OK");
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine(error);
            return 1;
        }
        finally
        {
            if (first != null) SeekerLauncher.DeleteRuntimeJar(first);
            if (second != null) SeekerLauncher.DeleteRuntimeJar(second);
            File.Delete(published);
            File.Delete(next);
            string previous = Path.Combine(fixture, ".runtime");
            if (Directory.Exists(previous)) Directory.Delete(previous);
            Directory.Delete(fixture);
        }
    }

    private static void Publish(string script, string source, string destination, bool success)
    {
        ProcessStartInfo start = new ProcessStartInfo("powershell.exe",
                "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + script
                + "\" -SourcePath \"" + source + "\" -DestinationPath \"" + destination + "\"");
        start.UseShellExecute = false;
        start.CreateNoWindow = true;
        start.WindowStyle = ProcessWindowStyle.Hidden;
        start.RedirectStandardOutput = true;
        start.RedirectStandardError = true;
        using (Process process = Process.Start(start))
        {
            string output = process.StandardOutput.ReadToEnd() + process.StandardError.ReadToEnd();
            process.WaitForExit();
            Check((process.ExitCode == 0) == success, "Unexpected publish result: " + output);
        }
    }

    private static void Check(bool condition, string message)
    {
        if (!condition) throw new Exception(message);
    }
}
