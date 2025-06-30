#
# Copyright 2024 Dynatrace LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
import json
import matplotlib.pyplot as plt
import glob


def plot_benchmark_results(file_name):

    f = open(file_name)
    data = json.load(f)

    d = {}

    for r in data:
        benchmark = r["benchmark"]

        benchmark_test, benchmark_algorithm = benchmark.rsplit(".", 1)

        if not benchmark_test in d:
            d[benchmark_test] = {}
        if not benchmark_algorithm in d[benchmark_test]:
            d[benchmark_test][benchmark_algorithm] = {}

        len = int(r["params"]["maxLength"])
        num_examples = int(r["params"]["numExamples"])
        time = float(r["primaryMetric"]["score"]) / num_examples
        d[benchmark_test][benchmark_algorithm][len] = time

    for benchmark_test in d:

        fig, ax = plt.subplots(1, 1)
        fig.set_size_inches(5, 5)
        ax.set_xlabel(r"maximum byte array length (bytes)")
        ax.set_ylabel(r"runtime (ns)")
        ax.set_yscale("log")
        ax.set_xscale("log")
        ax.set_xlim([1e0, 1e5])
        ax.grid()

        linestyles = ["solid", "dashdot", "dashed"]

        benchmark_algorithms = sorted(set(d[benchmark_test]))
        print(benchmark_algorithms)

        benchmark_algorithms = [
            (
                "testHashCodeJavaWithoutIntrinsic",
                "OpenJDK 24 default implementation",
                "#e76f51",
                "solid",
            ),
            (
                "testHashCodeJava",
                "OpenJDK 24 intrinsic implementation",
                "#f4a261",
                "solid",
            ),
            ("testHashCodeUnroll8", "loop unrolling", "#e9c46a", "solid"),
            ("testHashCodeSWAR", "new SWAR approach", "#2a9d8f", "solid"),
            ("testHashCodeSIMD", "new SIMD approach", "#264653", "solid"),
        ]

        for benchmark_algorithm, label, line_color, line_style in benchmark_algorithms:

            len_values = sorted(d[benchmark_test][benchmark_algorithm])
            time_values = [
                d[benchmark_test][benchmark_algorithm][len] for len in len_values
            ]
            ax.plot(
                len_values,
                time_values,
                label=label,
                color=line_color,
                linestyle=line_style,
            )

        fig.subplots_adjust(top=0.98, bottom=0.09, left=0.11, right=0.97)

        ax.legend(loc="upper left", ncol=1)

        fig.savefig(
            file_name[:-5] + "-" + benchmark_test[14:] + ".png",
            format="png",
            dpi=300,
            metadata={"CreationDate": None, "ModDate": None},
        )
        plt.close(fig)


for file_name in glob.glob("benchmark-results/*.json"):
    print(file_name)
    plot_benchmark_results(file_name)
