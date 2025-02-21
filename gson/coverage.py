"""
    Begin with running:
    
    `mvn test -pl gson -am` 
    
    This will generate a `coverage.txt` file. Then run this program, which 
    will prompt you to input the number of measurement points you 
    have placed in the code file that generated `coverage.txt`. 
    Then this program will output a new file called `paths.txt` which 
    will display the paths taken by the program as well as 
    which branch points were hit.
"""

n = int(input())
b = [False] * n

with open("coverage.txt", "r") as file:
    lines = file.readlines()

m = {}
a = []
for line in lines:
    s = line.strip()
    if s == "start":
        a = []
    elif s == "end":
        h = " ".join(a)
        if h in m:
            m[h] += 1
        else:
            m[h] = 1
    else:
        b[int(s) - 1] = True
        a.append(s)

with open("paths.txt", "w") as file:
    for k, v in m.items():
        file.write(f"{k}: {v}\n")
    file.write("\n")
    file.write(" ".join(f"{x:<2}" for x in range(1, n + 1)))
    file.write("\n")
    file.write(" ".join(f"{'O' if x else 'X':<2}" for x in b))
    file.write("\n")
