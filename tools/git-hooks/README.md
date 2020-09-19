This directory contains hooks that contributors should call in their git repo.

On a unix system, in git's top level folder, type
```bash
ln -s tools/git-hooks/pre-push .git/hooks/
```
to ensure that pre-push is executed before push.
