#include <errno.h>
#include <dirent.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/sendfile.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <sys/xattr.h>
#include <linux/stat.h>
#include <signal.h>
#include <unistd.h>

#ifndef __NR_openat2
#define __NR_openat2 437
#endif
#ifndef __NR_copy_file_range
#define __NR_copy_file_range 285
#endif
#ifndef __NR_close_range
#define __NR_close_range 436
#endif
#ifndef __NR_preadv2
#define __NR_preadv2 286
#endif
#ifndef __NR_pwritev2
#define __NR_pwritev2 287
#endif
#ifndef __NR_statx
#define __NR_statx 291
#endif
#ifndef CLOSE_RANGE_CLOEXEC
#define CLOSE_RANGE_CLOEXEC (1U << 2)
#endif
#ifndef SEEK_DATA
#define SEEK_DATA 3
#endif
#ifndef SEEK_HOLE
#define SEEK_HOLE 4
#endif
#ifndef RWF_DSYNC
#define RWF_DSYNC 0x00000002
#endif
#ifndef RWF_NOWAIT
#define RWF_NOWAIT 0x00000008
#endif
#ifndef RWF_APPEND
#define RWF_APPEND 0x00000010
#endif

#ifndef FICLONE
#define FICLONE _IOW(0x94, 9, int)
#endif

struct kf_open_how {
	unsigned long long flags;
	unsigned long long mode;
	unsigned long long resolve;
};

static const char native_marker[] __attribute__((used)) = "BASE-NATIVE-MARKER";

static int mmap_write(const char *path, off_t offset, const char *value)
{
	int fd;
	struct stat statl;
	void *mapping;
	size_t length = strlen(value);
	size_t mapping_length;

	fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("open");
		return 2;
	}
	if (fstat(fd, &statl) != 0) {
		perror("fstat");
		close(fd);
		return 3;
	}
	if (offset < 0 || length == 0 || offset + (off_t) length > statl.st_size) {
		fprintf(stderr, "invalid mmap range\n");
		close(fd);
		return 4;
	}
	mapping_length = (size_t) statl.st_size;
	mapping = mmap(NULL, mapping_length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
	if (mapping == MAP_FAILED) {
		perror("mmap");
		close(fd);
		return 5;
	}
	memcpy((char *) mapping + offset, value, length);
	if (msync(mapping, mapping_length, MS_SYNC) != 0) {
		perror("msync");
		munmap(mapping, mapping_length);
		close(fd);
		return 6;
	}
	munmap(mapping, mapping_length);
	close(fd);
	return 0;
}

static int fd_chmod(const char *path, mode_t mode)
{
	int fd = open(path, O_RDONLY | O_CLOEXEC);
	if (fd < 0) {
		perror("open");
		return 2;
	}
	if (fchmod(fd, mode) != 0) {
		perror("fchmod");
		close(fd);
		return 3;
	}
	close(fd);
	return 0;
}

static int path_setxattr(const char *path, const char *name, const char *value)
{
	if (setxattr(path, name, value, strlen(value), 0) != 0) {
		perror("setxattr");
		return 2;
	}
	return 0;
}

static int path_getxattr(const char *path, const char *name)
{
	char value[256];
	ssize_t size = getxattr(path, name, value, sizeof(value) - 1);
	if (size < 0) {
		perror("getxattr");
		return 2;
	}
	value[size] = '\0';
	printf("%s\n", value);
	return 0;
}

static int fd_setxattr(const char *path, const char *name, const char *value)
{
	int fd = open(path, O_RDONLY | O_CLOEXEC);
	if (fd < 0) {
		perror("open");
		return 2;
	}
	if (fsetxattr(fd, name, value, strlen(value), 0) != 0) {
		perror("fsetxattr");
		close(fd);
		return 3;
	}
	close(fd);
	return 0;
}

static int unix_bind(const char *path)
{
	struct sockaddr_un address;
	int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
	if (fd < 0) {
		perror("socket");
		return 2;
	}
	if (strlen(path) >= sizeof(address.sun_path)) {
		close(fd);
		return 3;
	}
	memset(&address, 0, sizeof(address));
	address.sun_family = AF_UNIX;
	strcpy(address.sun_path, path);
	if (bind(fd, (struct sockaddr *) &address, sizeof(address)) != 0) {
		perror("bind");
		close(fd);
		return 4;
	}
	close(fd);
	return 0;
}

static int read_directory(DIR *directory, char *output, size_t capacity)
{
	struct dirent *entry;
	size_t used = 0;
	while ((entry = readdir(directory)) != NULL) {
		int written;
		if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0)
			continue;
		written = snprintf(output + used, capacity - used, "%s\n", entry->d_name);
		if (written < 0 || (size_t) written >= capacity - used)
			return 2;
		used += (size_t) written;
	}
	return 0;
}

static int directory_rewind(const char *path)
{
	char first[8192] = "";
	char second[8192] = "";
	DIR *directory = opendir(path);
	int status;
	if (directory == NULL) {
		perror("opendir");
		return 2;
	}
	status = read_directory(directory, first, sizeof(first));
	if (status == 0) {
		rewinddir(directory);
		status = read_directory(directory, second, sizeof(second));
	}
	closedir(directory);
	if (status != 0)
		return status;
	if (strcmp(first, second) != 0) {
		fprintf(stderr, "directory rewind mismatch\nfirst:\n%ssecond:\n%s", first,
			second);
		return 3;
	}
	printf("%s", first);
	return 0;
}

static int openat2_write(const char *path, const char *value)
{
	struct kf_open_how how = { .flags = O_RDWR | O_CLOEXEC };
	const unsigned long long original_flags = how.flags;
	int fd = (int) syscall(__NR_openat2, AT_FDCWD, path, &how, sizeof(how));
	if (fd < 0) {
		perror("openat2");
		return 2;
	}
	if (how.flags != original_flags) {
		fprintf(stderr, "openat2 input structure was not restored\n");
		close(fd);
		return 3;
	}
	if (pwrite(fd, value, strlen(value), 0) != (ssize_t) strlen(value)) {
		perror("openat2 pwrite");
		close(fd);
		return 4;
	}
	close(fd);
	return 0;
}

static int creat_write(const char *path, const char *value)
{
	int fd = creat(path, 0644);
	if (fd < 0) {
		perror("creat");
		return 2;
	}
	if (write(fd, value, strlen(value)) != (ssize_t) strlen(value)) {
		perror("creat write");
		close(fd);
		return 3;
	}
	close(fd);
	return 0;
}

static int require_unsupported(long result, const char *operation)
{
	if (result == -1 && errno == EOPNOTSUPP)
		return 0;
	fprintf(stderr, "%s was not fail-closed: result=%ld errno=%d\n",
		operation, result, errno);
	return 1;
}

static int destructive_guards(const char *path, const char *source_path)
{
	char before[32] = { 0 };
	char after[32] = { 0 };
	int pipe_fds[2] = { -1, -1 };
	int source = -1;
	int destination = -1;
	int status = 0;

	destination = open(path, O_RDWR | O_CLOEXEC);
	source = open(source_path, O_RDONLY | O_CLOEXEC);
	if (destination < 0 || source < 0 ||
		pread(destination, before, sizeof(before), 0) <= 0) {
		perror("guard open/read");
		status = 2;
		goto done;
	}
	errno = 0;
	if (require_unsupported(fallocate(destination, 0, 0, 4096),
		"fallocate") != 0) {
		status = 3;
		goto done;
	}
	errno = 0;
	if (require_unsupported(ioctl(destination, FICLONE, source),
		"FICLONE") != 0) {
		status = 4;
		goto done;
	}
	errno = 0;
	if (require_unsupported(sendfile(destination, source, NULL, 16),
		"sendfile") != 0) {
		status = 5;
		goto done;
	}
	errno = 0;
	if (require_unsupported(syscall(__NR_copy_file_range, source, NULL,
		destination, NULL, 16, 0), "copy_file_range") != 0) {
		status = 6;
		goto done;
	}
	if (pipe(pipe_fds) != 0) {
		perror("guard pipe");
		status = 7;
		goto done;
	}
	errno = 0;
	if (require_unsupported(splice(destination, NULL, pipe_fds[1], NULL,
		16, 0), "splice") != 0) {
		status = 8;
		goto done;
	}
	if (pread(destination, after, sizeof(after), 0) <= 0 ||
		memcmp(before, after, sizeof(before)) != 0) {
		fprintf(stderr, "fail-closed operations changed visible data\n");
		status = 9;
	}

done:
	if (pipe_fds[0] >= 0)
		close(pipe_fds[0]);
	if (pipe_fds[1] >= 0)
		close(pipe_fds[1]);
	if (source >= 0)
		close(source);
	if (destination >= 0)
		close(destination);
	return status;
}

static int close_range_lifecycle(const char *path)
{
	const char marker[] = "CLOSE-RANGE";
	char verify[sizeof(marker) - 1] = { 0 };
	int fd = open(path, O_RDWR);
	if (fd < 0) {
		perror("close_range open");
		return 2;
	}
	if (syscall(__NR_close_range, (unsigned int) fd + 1, ~0U, 0) != 0) {
		perror("close_range preserve");
		close(fd);
		return 3;
	}
	if (pwrite(fd, marker, sizeof(marker) - 1, 0) !=
		(ssize_t) (sizeof(marker) - 1)) {
		perror("close_range preserved fd write");
		close(fd);
		return 4;
	}
	if (syscall(__NR_close_range, (unsigned int) fd,
		(unsigned int) fd, CLOSE_RANGE_CLOEXEC) != 0 ||
		(fcntl(fd, F_GETFD) & FD_CLOEXEC) == 0) {
		perror("close_range cloexec");
		close(fd);
		return 5;
	}
	if (syscall(__NR_close_range, (unsigned int) fd, ~0U, 0) != 0) {
		perror("close_range close");
		return 6;
	}
	errno = 0;
	if (fcntl(fd, F_GETFD) != -1 || errno != EBADF) {
		fprintf(stderr, "close_range did not close managed fd\n");
		return 7;
	}
	fd = open(path, O_RDONLY | O_CLOEXEC);
	if (fd < 0 || pread(fd, verify, sizeof(verify), 0) !=
		(ssize_t) sizeof(verify) || memcmp(verify, marker, sizeof(verify)) != 0) {
		perror("close_range reopen");
		if (fd >= 0)
			close(fd);
		return 8;
	}
	close(fd);
	return 0;
}

static int close_range_probe(const char *first_text, const char *last_text)
{
	char *end;
	unsigned long first;
	unsigned long last;
	errno = 0;
	first = strtoul(first_text, &end, 0);
	if (errno != 0 || *end != '\0' || first > UINT_MAX)
		return 2;
	errno = 0;
	last = strtoul(last_text, &end, 0);
	if (errno != 0 || *end != '\0' || last > UINT_MAX)
		return 3;
	errno = 0;
	if (syscall(__NR_close_range, (unsigned int) first,
		(unsigned int) last, 0) != 0) {
		printf("errno=%d\n", errno);
		return 4;
	}
	printf("ok\n");
	return 0;
}

static ssize_t raw_pwritev2(int fd, const struct iovec *iov, int count,
	off_t offset, int flags)
{
	uint64_t value = (uint64_t) (int64_t) offset;
	return syscall(__NR_pwritev2, fd, iov, count, (uint32_t) value,
		(uint32_t) (value >> 32), flags);
}

static ssize_t raw_preadv2(int fd, const struct iovec *iov, int count,
	off_t offset, int flags)
{
	uint64_t value = (uint64_t) (int64_t) offset;
	return syscall(__NR_preadv2, fd, iov, count, (uint32_t) value,
		(uint32_t) (value >> 32), flags);
}

static int vector_io_lifecycle(const char *path)
{
	const off_t high_offset = ((off_t) 4 << 30) + 123;
	const char first[] = "VEC";
	const char second[] = "TOR";
	const char position_marker[] = "POS";
	const char append_marker[] = "APPEND";
	char read_first[sizeof(first)] = { 0 };
	char read_second[sizeof(second)] = { 0 };
	char append_verify[sizeof(append_marker)] = { 0 };
	struct iovec write_vector[2] = {
		{ .iov_base = (void *) first, .iov_len = sizeof(first) - 1 },
		{ .iov_base = (void *) second, .iov_len = sizeof(second) - 1 }
	};
	struct iovec read_vector[2] = {
		{ .iov_base = read_first, .iov_len = sizeof(first) - 1 },
		{ .iov_base = read_second, .iov_len = sizeof(second) - 1 }
	};
	struct iovec position_vector = {
		.iov_base = (void *) position_marker,
		.iov_len = sizeof(position_marker) - 1
	};
	struct iovec append_vector = {
		.iov_base = (void *) append_marker,
		.iov_len = sizeof(append_marker) - 1
	};
	struct stat stat_value;
	off_t append_offset;
	int fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("vector lifecycle open");
		return 2;
	}
	if (raw_pwritev2(fd, write_vector, 2, high_offset, RWF_DSYNC) != 6 ||
		raw_preadv2(fd, read_vector, 2, high_offset, 0) != 6 ||
		memcmp(read_first, first, sizeof(first) - 1) != 0 ||
		memcmp(read_second, second, sizeof(second) - 1) != 0) {
		perror("vector lifecycle high offset");
		close(fd);
		return 3;
	}
	if (lseek(fd, 100, SEEK_SET) != 100 ||
		raw_pwritev2(fd, &position_vector, 1, (off_t) -1, 0) != 3 ||
		lseek(fd, 0, SEEK_CUR) != 103) {
		perror("vector lifecycle offset minus one");
		close(fd);
		return 4;
	}
	if (fstat(fd, &stat_value) != 0) {
		perror("vector lifecycle fstat");
		close(fd);
		return 5;
	}
	append_offset = stat_value.st_size;
	if (raw_pwritev2(fd, &append_vector, 1, 0, RWF_APPEND) != 6 ||
		lseek(fd, 0, SEEK_CUR) != 103 ||
		pread(fd, append_verify, sizeof(append_marker) - 1, append_offset) != 6 ||
		memcmp(append_verify, append_marker, sizeof(append_marker) - 1) != 0) {
		perror("vector lifecycle append");
		close(fd);
		return 6;
	}
	errno = 0;
	if (raw_pwritev2(fd, &position_vector, 1, 0, RWF_NOWAIT) != -1 ||
		errno != EOPNOTSUPP) {
		fprintf(stderr, "unsupported RWF_NOWAIT was not rejected: %d\n", errno);
		close(fd);
		return 7;
	}
	close(fd);
	return 0;
}

static int statx_size(const char *path, const char *expected_text)
{
	char *end;
	unsigned long long expected;
	struct statx stat_value;
	errno = 0;
	expected = strtoull(expected_text, &end, 0);
	if (errno != 0 || *end != '\0')
		return 2;
	memset(&stat_value, 0, sizeof(stat_value));
	if (syscall(__NR_statx, AT_FDCWD, path, 0, STATX_SIZE, &stat_value) != 0) {
		perror("statx");
		return 3;
	}
	if ((stat_value.stx_mask & STATX_SIZE) == 0 ||
		stat_value.stx_size != expected) {
		fprintf(stderr, "statx size mismatch: %llu != %llu\n",
			(unsigned long long) stat_value.stx_size, expected);
		return 4;
	}
	return 0;
}

static int abi32_verify(const char *path)
{
	const off_t high_offset = ((off_t) 4 << 30) + 123;
	const off_t high_page = ((off_t) 4 << 30) + 65536;
	const off_t expected_size = ((off_t) 5 << 30) + 8192;
	const char scalar_marker[] = "ABI32";
	const char mmap_marker[] = "MAP32";
	char buffer[sizeof(scalar_marker)] = { 0 };
	struct stat path_stat;
	struct stat fd_stat;
	int fd;

	if (sizeof(void *) != 4) {
		fprintf(stderr, "abi32 fixture is not a 32-bit executable\n");
		return 2;
	}
	if (stat(path, &path_stat) != 0 || path_stat.st_size != expected_size) {
		perror("abi32 path stat");
		return 3;
	}
	fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0 || fstat(fd, &fd_stat) != 0 || fd_stat.st_size != expected_size) {
		perror("abi32 fd stat");
		if (fd >= 0)
			close(fd);
		return 4;
	}
	if (lseek(fd, 0, SEEK_END) != expected_size) {
		perror("abi32 llseek end");
		close(fd);
		return 5;
	}
	if (pread(fd, buffer, sizeof(scalar_marker) - 1, high_offset) !=
		(ssize_t) (sizeof(scalar_marker) - 1) ||
		memcmp(buffer, scalar_marker, sizeof(scalar_marker) - 1) != 0) {
		perror("abi32 scalar verify");
		close(fd);
		return 6;
	}
	memset(buffer, 0, sizeof(buffer));
	if (pread(fd, buffer, sizeof(mmap_marker) - 1, high_page) !=
		(ssize_t) (sizeof(mmap_marker) - 1) ||
		memcmp(buffer, mmap_marker, sizeof(mmap_marker) - 1) != 0) {
		perror("abi32 mmap verify");
		close(fd);
		return 7;
	}
	close(fd);
	return 0;
}

static int abi32_lifecycle(const char *path)
{
	const off_t high_offset = ((off_t) 4 << 30) + 123;
	const off_t high_page = ((off_t) 4 << 30) + 65536;
	const off_t expected_size = ((off_t) 5 << 30) + 8192;
	const char scalar_marker[] = "ABI32";
	const char mmap_marker[] = "MAP32";
	struct flock lock = {
		.l_type = F_WRLCK,
		.l_whence = SEEK_SET,
		.l_start = high_offset,
		.l_len = 1
	};
	void *mapping;
	int fd;

	if (sizeof(void *) != 4) {
		fprintf(stderr, "abi32 fixture is not a 32-bit executable\n");
		return 2;
	}
	if (truncate(path, expected_size) != 0) {
		perror("abi32 truncate64");
		return 3;
	}
	fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("abi32 open");
		return 4;
	}
	if (ftruncate(fd, expected_size) != 0) {
		perror("abi32 ftruncate64");
		close(fd);
		return 5;
	}
	if (pwrite(fd, scalar_marker, sizeof(scalar_marker) - 1, high_offset) !=
		(ssize_t) (sizeof(scalar_marker) - 1)) {
		perror("abi32 pwrite64");
		close(fd);
		return 6;
	}
	mapping = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd,
		high_page);
	if (mapping == MAP_FAILED) {
		perror("abi32 mmap2");
		close(fd);
		return 7;
	}
	memcpy(mapping, mmap_marker, sizeof(mmap_marker) - 1);
	if (msync(mapping, 4096, MS_SYNC) != 0) {
		perror("abi32 msync");
		munmap(mapping, 4096);
		close(fd);
		return 8;
	}
	munmap(mapping, 4096);
	if (fcntl(fd, F_SETLK, &lock) != 0) {
		perror("abi32 fcntl lock");
		close(fd);
		return 9;
	}
	lock.l_type = F_UNLCK;
	if (fcntl(fd, F_SETLK, &lock) != 0) {
		perror("abi32 fcntl unlock");
		close(fd);
		return 10;
	}
	if (fsync(fd) != 0) {
		perror("abi32 fsync");
		close(fd);
		return 11;
	}
	close(fd);
	return abi32_verify(path);
}

static int lseek_data_hole(const char *path)
{
	struct stat stat_value;
	int fd = open(path, O_RDONLY | O_CLOEXEC);
	if (fd < 0 || fstat(fd, &stat_value) != 0) {
		perror("lseek data/hole open");
		if (fd >= 0)
			close(fd);
		return 2;
	}
	if (lseek(fd, 0, SEEK_DATA) != 0 ||
		lseek(fd, 0, SEEK_HOLE) != stat_value.st_size) {
		perror("lseek data/hole range");
		close(fd);
		return 3;
	}
	errno = 0;
	if (lseek(fd, stat_value.st_size, SEEK_DATA) != (off_t) -1 ||
		errno != ENXIO) {
		fprintf(stderr, "SEEK_DATA at EOF did not return ENXIO\n");
		close(fd);
		return 4;
	}
	errno = 0;
	if (lseek(fd, stat_value.st_size, SEEK_HOLE) != (off_t) -1 ||
		errno != ENXIO) {
		fprintf(stderr, "SEEK_HOLE at EOF did not return ENXIO\n");
		close(fd);
		return 5;
	}
	close(fd);
	return 0;
}

static int mmap_grow(const char *path)
{
	const off_t size = 128 * 1024;
	const off_t offset = 96 * 1024;
	const char marker[] = "GROW";
	unsigned char *mapping;
	char verify[sizeof(marker)] = { 0 };
	int fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("grow open");
		return 2;
	}
	if (ftruncate(fd, size) != 0) {
		perror("grow truncate");
		close(fd);
		return 3;
	}
	mapping = mmap(NULL, (size_t) size, PROT_READ | PROT_WRITE,
		MAP_SHARED, fd, 0);
	if (mapping == MAP_FAILED) {
		perror("grow mmap");
		close(fd);
		return 4;
	}
	if (mapping[offset - 1] != 0 || mapping[offset + 4096] != 0) {
		fprintf(stderr, "grown mmap region is not zero-filled\n");
		munmap(mapping, (size_t) size);
		close(fd);
		return 5;
	}
	memcpy(mapping + offset, marker, sizeof(marker));
	if (msync(mapping, (size_t) size, MS_SYNC) != 0) {
		perror("grow msync");
		munmap(mapping, (size_t) size);
		close(fd);
		return 6;
	}
	munmap(mapping, (size_t) size);
	if (pread(fd, verify, sizeof(verify), offset) != (ssize_t) sizeof(verify) ||
		memcmp(verify, marker, sizeof(marker)) != 0) {
		fprintf(stderr, "grown mmap write was not persisted\n");
		close(fd);
		return 7;
	}
	close(fd);
	return 0;
}

static int mmap_lifecycle(const char *path)
{
	const size_t page = 4096;
	const size_t length = 2 * 64 * 1024;
	unsigned char *mapping;
	char verify[2] = { 0 };
	void *moved;
	int fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("lifecycle open");
		return 2;
	}
	mapping = mmap(NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
	if (mapping == MAP_FAILED) {
		perror("lifecycle mmap");
		close(fd);
		return 3;
	}
	errno = 0;
	moved = mremap(mapping, length, length + page, MREMAP_MAYMOVE);
	if (moved != MAP_FAILED || errno != EOPNOTSUPP) {
		fprintf(stderr, "managed mremap was not rejected: moved=%p errno=%d\n",
			moved, errno);
		if (moved != MAP_FAILED)
			munmap(moved, length + page);
		else
			munmap(mapping, length);
		close(fd);
		return 4;
	}
	if (mprotect(mapping, length, PROT_READ) != 0 ||
		mprotect(mapping + 64 * 1024, 64 * 1024,
			PROT_READ | PROT_WRITE) != 0) {
		perror("lifecycle mprotect");
		munmap(mapping, length);
		close(fd);
		return 5;
	}
	mapping[64 * 1024] = 'A';
	if (munmap(mapping, page) != 0) {
		perror("lifecycle partial munmap");
		munmap(mapping + page, length - page);
		close(fd);
		return 6;
	}
	mapping[64 * 1024 + 1] = 'B';
	if (msync(mapping + 64 * 1024, 64 * 1024, MS_SYNC) != 0) {
		perror("lifecycle msync");
		munmap(mapping + page, length - page);
		close(fd);
		return 7;
	}
	if (munmap(mapping + page, length - page) != 0) {
		perror("lifecycle final munmap");
		close(fd);
		return 8;
	}
	if (pread(fd, verify, sizeof(verify), 64 * 1024) !=
		(ssize_t) sizeof(verify) || verify[0] != 'A' || verify[1] != 'B') {
		fprintf(stderr, "mmap lifecycle data mismatch\n");
		close(fd);
		return 9;
	}
	close(fd);
	return 0;
}

static int mmap_truncate_lifecycle(const char *path)
{
	const size_t small_size = 64 * 1024;
	const size_t large_size = 128 * 1024;
	const size_t probe_offset = 96 * 1024;
	const char marker[] = "REGROWN";
	unsigned char *mapping;
	char verify[sizeof(marker)] = { 0 };
	pid_t child;
	int child_status;
	int fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("truncate lifecycle open");
		return 2;
	}
	mapping = mmap(NULL, large_size, PROT_READ | PROT_WRITE,
		MAP_SHARED, fd, 0);
	if (mapping == MAP_FAILED) {
		perror("truncate lifecycle mmap");
		close(fd);
		return 3;
	}
	if (ftruncate(fd, (off_t) small_size) != 0) {
		perror("truncate lifecycle shrink");
		munmap(mapping, large_size);
		close(fd);
		return 4;
	}
	child = fork();
	if (child < 0) {
		perror("truncate lifecycle fork");
		munmap(mapping, large_size);
		close(fd);
		return 5;
	}
	if (child == 0) {
		volatile unsigned char observed = mapping[probe_offset];
		_exit(observed == 0 ? 41 : 42);
	}
	if (waitpid(child, &child_status, 0) != child ||
		!WIFSIGNALED(child_status) || WTERMSIG(child_status) != SIGBUS) {
		fprintf(stderr, "truncate lifecycle expected SIGBUS, status=%d\n",
			child_status);
		munmap(mapping, large_size);
		close(fd);
		return 6;
	}
	if (ftruncate(fd, (off_t) large_size) != 0) {
		perror("truncate lifecycle regrow");
		munmap(mapping, large_size);
		close(fd);
		return 7;
	}
	if (mapping[probe_offset] != 0) {
		fprintf(stderr, "regrown mmap page was not zero-filled\n");
		munmap(mapping, large_size);
		close(fd);
		return 8;
	}
	memcpy(mapping + probe_offset, marker, sizeof(marker));
	if (msync(mapping, large_size, MS_SYNC) != 0) {
		perror("truncate lifecycle msync");
		munmap(mapping, large_size);
		close(fd);
		return 9;
	}
	if (pread(fd, verify, sizeof(verify), (off_t) probe_offset) !=
		(ssize_t) sizeof(verify) || memcmp(verify, marker, sizeof(marker)) != 0) {
		fprintf(stderr, "regrown mmap write was not persisted\n");
		munmap(mapping, large_size);
		close(fd);
		return 10;
	}
	munmap(mapping, large_size);
	close(fd);
	return 0;
}

static int fd_rename_lifecycle(const char *source, const char *destination)
{
	const char first[] = "FD-RENAMED";
	const char second[] = "PATH-RENAMED";
	char verify[sizeof(second)] = { 0 };
	int fd = open(source, O_RDWR | O_CLOEXEC);
	int reopened;
	if (fd < 0) {
		perror("rename lifecycle open");
		return 2;
	}
	if (rename(source, destination) != 0) {
		perror("rename lifecycle rename");
		close(fd);
		return 3;
	}
	if (pwrite(fd, first, sizeof(first), 0) != (ssize_t) sizeof(first)) {
		perror("rename lifecycle old fd write");
		close(fd);
		return 4;
	}
	reopened = open(destination, O_RDWR | O_CLOEXEC);
	if (reopened < 0) {
		perror("rename lifecycle reopen");
		close(fd);
		return 5;
	}
	if (pwrite(reopened, second, sizeof(second), 32) !=
		(ssize_t) sizeof(second) ||
		pread(fd, verify, sizeof(verify), 32) != (ssize_t) sizeof(verify) ||
		memcmp(verify, second, sizeof(second)) != 0) {
		perror("rename lifecycle shared identity");
		close(reopened);
		close(fd);
		return 6;
	}
	close(reopened);
	close(fd);
	return 0;
}

static int fd_rename_overwrite_lifecycle(const char *source,
	const char *destination)
{
	const char old_marker[] = "OLD-DESTINATION";
	const char new_marker[] = "NEW-SOURCE";
	char verify[sizeof(old_marker)] = { 0 };
	int destination_fd = open(destination, O_RDWR | O_CLOEXEC);
	int source_fd = open(source, O_RDWR | O_CLOEXEC);
	int reopened;
	if (destination_fd < 0 || source_fd < 0) {
		perror("rename overwrite open");
		if (destination_fd >= 0)
			close(destination_fd);
		if (source_fd >= 0)
			close(source_fd);
		return 2;
	}
	if (rename(source, destination) != 0) {
		perror("rename overwrite rename");
		return 3;
	}
	if (pwrite(destination_fd, old_marker, sizeof(old_marker), 0) !=
		(ssize_t) sizeof(old_marker) ||
		pwrite(source_fd, new_marker, sizeof(new_marker), 0) !=
		(ssize_t) sizeof(new_marker)) {
		perror("rename overwrite fd write");
		return 4;
	}
	reopened = open(destination, O_RDONLY | O_CLOEXEC);
	if (reopened < 0 ||
		pread(reopened, verify, sizeof(new_marker), 0) !=
			(ssize_t) sizeof(new_marker) ||
		memcmp(verify, new_marker, sizeof(new_marker)) != 0) {
		perror("rename overwrite path identity");
		return 5;
	}
	memset(verify, 0, sizeof(verify));
	if (pread(destination_fd, verify, sizeof(old_marker), 0) !=
		(ssize_t) sizeof(old_marker) ||
		memcmp(verify, old_marker, sizeof(old_marker)) != 0) {
		fprintf(stderr, "overwritten open fd lost its inode identity\n");
		return 6;
	}
	close(reopened);
	close(source_fd);
	close(destination_fd);
	return 0;
}

static int fd_unlink_lifecycle(const char *path)
{
	const size_t mapping_size = 64 * 1024;
	const char fd_marker[] = "UNLINKED-FD";
	const char map_marker[] = "UNLINKED-MAP";
	const char new_marker[] = "NEW-PATH";
	char verify[sizeof(map_marker)] = { 0 };
	unsigned char *mapping;
	int fd = open(path, O_RDWR | O_CLOEXEC);
	int recreated;
	if (fd < 0) {
		perror("unlink lifecycle open");
		return 2;
	}
	if (unlink(path) != 0 ||
		pwrite(fd, fd_marker, sizeof(fd_marker), 0) !=
			(ssize_t) sizeof(fd_marker)) {
		perror("unlink lifecycle unlink/write");
		close(fd);
		return 3;
	}
	mapping = mmap(NULL, mapping_size, PROT_READ | PROT_WRITE,
		MAP_SHARED, fd, 0);
	if (mapping == MAP_FAILED) {
		perror("unlink lifecycle mmap");
		close(fd);
		return 4;
	}
	memcpy(mapping + 4096, map_marker, sizeof(map_marker));
	if (msync(mapping, mapping_size, MS_SYNC) != 0 ||
		pread(fd, verify, sizeof(verify), 4096) != (ssize_t) sizeof(verify) ||
		memcmp(verify, map_marker, sizeof(map_marker)) != 0) {
		perror("unlink lifecycle mmap persistence");
		return 5;
	}
	recreated = open(path, O_RDWR | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
	if (recreated < 0 || write(recreated, new_marker, sizeof(new_marker)) !=
		(ssize_t) sizeof(new_marker)) {
		perror("unlink lifecycle recreate");
		return 6;
	}
	memset(verify, 0, sizeof(verify));
	if (pread(fd, verify, sizeof(fd_marker), 0) !=
		(ssize_t) sizeof(fd_marker) ||
		memcmp(verify, fd_marker, sizeof(fd_marker)) != 0) {
		fprintf(stderr, "recreated path replaced the unlinked open inode\n");
		return 7;
	}
	close(recreated);
	munmap(mapping, mapping_size);
	close(fd);
	return 0;
}

static int replace_marker(const char *path, const char *from, const char *to)
{
	struct stat stat_value;
	char *buffer;
	char *match = NULL;
	size_t from_length = strlen(from);
	int fd;
	int status = 0;

	if (from_length == 0 || from_length != strlen(to)) {
		fprintf(stderr, "marker lengths differ\n");
		return 2;
	}
	fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("open");
		return 3;
	}
	if (fstat(fd, &stat_value) != 0 || stat_value.st_size <= 0) {
		perror("fstat");
		close(fd);
		return 4;
	}
	buffer = malloc((size_t) stat_value.st_size);
	if (buffer == NULL) {
		close(fd);
		return 5;
	}
	if (pread(fd, buffer, (size_t) stat_value.st_size, 0) != stat_value.st_size) {
		perror("pread");
		status = 6;
		goto done;
	}
	for (off_t offset = 0;
		offset + (off_t) from_length <= stat_value.st_size; offset++) {
		if (memcmp(buffer + offset, from, from_length) != 0)
			continue;
		if (match != NULL) {
			fprintf(stderr, "marker is not unique\n");
			status = 7;
			goto done;
		}
		match = buffer + offset;
	}
	if (match == NULL) {
		fprintf(stderr, "marker not found\n");
		status = 8;
		goto done;
	}
	if (pwrite(fd, to, from_length, (off_t) (match - buffer)) !=
		(ssize_t) from_length) {
		perror("pwrite");
		status = 9;
		goto done;
	}
	if (fsync(fd) != 0) {
		perror("fsync");
		status = 10;
	}

done:
	free(buffer);
	close(fd);
	return status;
}

static int lock_test(const char *path, int use_flock)
{
	int parent_to_child[2];
	int child_to_parent[2];
	int fd;
	int alias;
	pid_t child;
	int child_status;
	char byte;
	struct flock lock;

	fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		perror("lock open");
		return 2;
	}
	alias = dup(fd);
	if (alias < 0) {
		perror("lock dup");
		close(fd);
		return 3;
	}
	close(fd);
	memset(&lock, 0, sizeof(lock));
	lock.l_type = F_WRLCK;
	lock.l_whence = SEEK_SET;
	if ((use_flock ? flock(alias, LOCK_EX | LOCK_NB) :
		fcntl(alias, F_SETLK, &lock)) != 0) {
		perror("parent lock");
		close(alias);
		return 4;
	}
	if (pipe(parent_to_child) != 0 || pipe(child_to_parent) != 0) {
		perror("pipe");
		close(alias);
		return 5;
	}
	child = fork();
	if (child < 0) {
		perror("fork");
		close(alias);
		return 6;
	}
	if (child == 0) {
		int competing;
		char byte;
		close(parent_to_child[1]);
		close(child_to_parent[0]);
		close(alias);
		competing = open(path, O_RDWR | O_CLOEXEC);
		if (competing < 0)
			_exit(20);
		errno = 0;
		if ((use_flock ? flock(competing, LOCK_EX | LOCK_NB) :
			fcntl(competing, F_SETLK, &lock)) == 0)
			_exit(21);
		if (errno != EACCES && errno != EAGAIN && errno != EWOULDBLOCK)
			_exit(22);
		byte = 'r';
		if (write(child_to_parent[1], &byte, 1) != 1 ||
			read(parent_to_child[0], &byte, 1) != 1)
			_exit(23);
		if ((use_flock ? flock(competing, LOCK_EX | LOCK_NB) :
			fcntl(competing, F_SETLK, &lock)) != 0)
			_exit(24);
		if (use_flock)
			(void) flock(competing, LOCK_UN);
		else {
			lock.l_type = F_UNLCK;
			(void) fcntl(competing, F_SETLK, &lock);
		}
		close(competing);
		_exit(0);
	}
	close(parent_to_child[0]);
	close(child_to_parent[1]);
	if (read(child_to_parent[0], &byte, 1) != 1) {
		close(alias);
		return 7;
	}
	if (use_flock) {
		if (flock(alias, LOCK_UN) != 0) {
			perror("parent unlock flock");
			close(alias);
			return 8;
		}
	}
	else {
		lock.l_type = F_UNLCK;
		if (fcntl(alias, F_SETLK, &lock) != 0) {
			perror("parent unlock fcntl");
			close(alias);
			return 9;
		}
	}
	close(alias);
	byte = 'g';
	if (write(parent_to_child[1], &byte, 1) != 1)
		return 10;
	close(parent_to_child[1]);
	close(child_to_parent[0]);
	if (waitpid(child, &child_status, 0) != child ||
		!WIFEXITED(child_status) || WEXITSTATUS(child_status) != 0) {
		fprintf(stderr, "%s child status=%d\n",
			use_flock ? "flock" : "fcntl", child_status);
		return 11;
	}
	return 0;
}

static int all_lock_tests(const char *path)
{
	int status = lock_test(path, 0);
	if (status != 0)
		return status;
	status = lock_test(path, 1);
	if (status != 0)
		return status + 20;
	return 0;
}

int main(int argc, char **argv)
{
	char *end;
	long value;

	if (argc < 2) {
		fprintf(stderr, "usage: kf-view-fixture <operation> ...\n");
		return 1;
	}
	if (strcmp(argv[1], "mmap-write") == 0) {
		if (argc != 5)
			return 1;
		errno = 0;
		value = strtol(argv[3], &end, 10);
		if (errno != 0 || *end != '\0' || value < 0)
			return 1;
		return mmap_write(argv[2], (off_t) value, argv[4]);
	}
	if (strcmp(argv[1], "openat2-write") == 0 && argc == 4)
		return openat2_write(argv[2], argv[3]);
	if (strcmp(argv[1], "creat-write") == 0 && argc == 4)
		return creat_write(argv[2], argv[3]);
	if (strcmp(argv[1], "destructive-guards") == 0 && argc == 4)
		return destructive_guards(argv[2], argv[3]);
	if (strcmp(argv[1], "close-range-lifecycle") == 0 && argc == 3)
		return close_range_lifecycle(argv[2]);
	if (strcmp(argv[1], "close-range-probe") == 0 && argc == 4)
		return close_range_probe(argv[2], argv[3]);
	if (strcmp(argv[1], "vector-io-lifecycle") == 0 && argc == 3)
		return vector_io_lifecycle(argv[2]);
	if (strcmp(argv[1], "statx-size") == 0 && argc == 4)
		return statx_size(argv[2], argv[3]);
	if (strcmp(argv[1], "abi32-lifecycle") == 0 && argc == 3)
		return abi32_lifecycle(argv[2]);
	if (strcmp(argv[1], "abi32-verify") == 0 && argc == 3)
		return abi32_verify(argv[2]);
	if (strcmp(argv[1], "lseek-data-hole") == 0 && argc == 3)
		return lseek_data_hole(argv[2]);
	if (strcmp(argv[1], "mmap-grow") == 0 && argc == 3)
		return mmap_grow(argv[2]);
	if (strcmp(argv[1], "mmap-lifecycle") == 0 && argc == 3)
		return mmap_lifecycle(argv[2]);
	if (strcmp(argv[1], "mmap-truncate-lifecycle") == 0 && argc == 3)
		return mmap_truncate_lifecycle(argv[2]);
	if (strcmp(argv[1], "fd-rename-lifecycle") == 0 && argc == 4)
		return fd_rename_lifecycle(argv[2], argv[3]);
	if (strcmp(argv[1], "fd-rename-overwrite-lifecycle") == 0 && argc == 4)
		return fd_rename_overwrite_lifecycle(argv[2], argv[3]);
	if (strcmp(argv[1], "fd-unlink-lifecycle") == 0 && argc == 3)
		return fd_unlink_lifecycle(argv[2]);
	if (strcmp(argv[1], "fd-chmod") == 0) {
		if (argc != 4)
			return 1;
		errno = 0;
		value = strtol(argv[3], &end, 8);
		if (errno != 0 || *end != '\0' || value < 0 || value > 07777)
			return 1;
		return fd_chmod(argv[2], (mode_t) value);
	}
	if (strcmp(argv[1], "setxattr") == 0 && argc == 5)
		return path_setxattr(argv[2], argv[3], argv[4]);
	if (strcmp(argv[1], "getxattr") == 0 && argc == 4)
		return path_getxattr(argv[2], argv[3]);
	if (strcmp(argv[1], "fd-setxattr") == 0 && argc == 5)
		return fd_setxattr(argv[2], argv[3], argv[4]);
	if (strcmp(argv[1], "unix-bind") == 0 && argc == 3)
		return unix_bind(argv[2]);
	if (strcmp(argv[1], "dir-rewind") == 0 && argc == 3)
		return directory_rewind(argv[2]);
	if (strcmp(argv[1], "print-native-marker") == 0 && argc == 2) {
		printf("%s\n", native_marker);
		return 0;
	}
	if (strcmp(argv[1], "replace-marker") == 0 && argc == 5)
		return replace_marker(argv[2], argv[3], argv[4]);
	if (strcmp(argv[1], "lock-test") == 0 && argc == 3)
		return all_lock_tests(argv[2]);
	fprintf(stderr, "unsupported operation: %s\n", argv[1]);
	return 1;
}
