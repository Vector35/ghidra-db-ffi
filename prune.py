from pathlib import Path


java_files = {}

dirs = [Path('src/main/java/db'), Path('src/main/java/ghidra')]


contents: dict[str, tuple[str, str]] = {}

while not len(dirs) == 0:
    cur_dir = dirs.pop()
    for i in cur_dir.iterdir():
        if i.is_dir():
            dirs.append(i)
        elif i.is_file():
            if i.name.endswith('.java'):
                class_name = i.name.removesuffix('.java')
                contents[class_name] = (str(i), i.read_text())

to_remove = []

for (class_name,(path,_)) in contents.items():
    keep = False
    for (k,(_,v)) in contents.items():
        if k == class_name:
            continue
        if class_name in v: # TODO: match word boundaries?
            keep = True
            break

    if not keep:
        to_remove.append(path)


print(to_remove)
input('')

for i in to_remove:
    Path(i).unlink()
