# irb

A reader in pure Java for the `*.irb` file format by InfraTec,  
inspired by https://github.com/tomsoftware/Irbis-File-Format .

This program can be used either as a Maven dependency within another program
or as a stand-alone commandline utility.

## Legal disclaimer

This is a pure hobby project. No copyright infringements or similar is intended.  
Please inform the author about possible legal issues before turning to a lawyer.


## Building

1. Download and install build dependencies
   1. JDK 1.8 (or any newer version)
   2. Maven
2. Build a runnable self-contained jar file:

```bash
> mvn clean package
```

The output will be at `target/irb-1.0.2.jar`.

## Use as a Maven dependency

You can include this project as a dependency in Maven:

```xml
<dependency>
    <groupId>de.labathome</groupId>
    <artifactId>irb</artifactId>
    <version>1.0.2</version>
</dependency>
```

The ready-to-use jar can also be directly downloaded here:
[irb-1.0.2.jar](https://github.com/jonathanschilling/irb/releases/download/v1.0.2/irb-1.0.2.jar)

## Command-line Usage

Run-time dependencies:
- Python3 packages
  - matplotlib
    - Requires libjpeg and zlib development headers for *pillow*
  - numpy

Execute the jar with the `*.irb` file as first command line argument:
 
```bash
> java -jar irb-1.0.2.jar AB020300.irb
```

It can also be run in headless mode, where no attempt will be made to plot the image using JyPlot:

```bash
> java -jar irb-1.0.2.jar --headless AB020300.irb
```

This will generate two text output files and a direct PNG equivalent of the data:
 * `AB020300.irb.img_0.dat` contains the raw image data in degree Celsius as a two-dimensional matrix.
   Each line in the file contains the temperatures for each pixel of the corresponding line of the image.
   The first line corresponds to the top of the image for easy plotting with e.g. Gnuplot:
   ```
   plot 'AB020300.irb.img_0.dat' matrix w image
   ```
 * `AB020300.irb.meta_0.json` contains the meta-data of the image in the JSON format.
 * `AB020300.irb.img_0.png` contains a direct PNG export of the image data
   with the temperature in degree Celsius mapped to a `jet`-like colorbar.

If not run in headless mode, a direct plot of the image is tried using `JyPlot`.
This requires to have a Python installation with `matplotlib` and `numpy` on your `$PATH`.
A temporary Python script file is created in a folder `PythonScript` in your home directory.
This will be executed by the default `python` command.
If JyPlot cannot find your `python` installation, it will print a corresponding stack trace of error messages.
You can fix this by telling JyPlot about your Python installation by creating a text file
`~/PythonScript/PYTHON_PATH.txt` which contains the absolute path to your Python executable
(`python` on Linux and Mac, `python.exe` on Windows).

The text output files should nevertheless get created.

## Contributers

 * [jonathanschilling](https://github.com/jonathanschilling)
 * [benjaminSchilling33](https://github.com/benjaminschilling33)
 * [uhoefel](https://github.com/uhoefel)

## License

SPDX-License-Identifier: Apache-2.0
