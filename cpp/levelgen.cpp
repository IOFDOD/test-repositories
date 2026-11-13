#include <algorithm>
#include <array>
#include <charconv>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <numeric>
#include <queue>
#include <random>
#include <string>
#include <vector>

// A perfect maze: every open cell is reachable from the start.
int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "Usage: levelgen <output.map> <seed 0..4294967295>\n";
        return 1;
    }
    std::uint32_t seed = 0;
    const std::string input = argv[2];
    auto parsed = std::from_chars(input.data(), input.data() + input.size(), seed);
    if (input.empty() || parsed.ec != std::errc{} || parsed.ptr != input.data() + input.size()) {
        std::cerr << "Invalid seed; expected an unsigned 32-bit integer.\n";
        return 1;
    }
    constexpr int size = 21;
    std::vector<std::string> map(size, std::string(size, '#'));
    std::mt19937 rng(seed);
    std::vector<std::pair<int, int>> stack{{1, 1}};
    map[1][1] = '.';
    const std::array<int, 4> dx{0, 1, 0, -1}, dy{-1, 0, 1, 0};
    while (!stack.empty()) {
        auto [x, y] = stack.back();
        std::vector<int> candidates;
        for (int i = 0; i < 4; ++i) {
            int nx = x + 2 * dx[i], ny = y + 2 * dy[i];
            if (nx > 0 && nx < size - 1 && ny > 0 && ny < size - 1 && map[ny][nx] == '#')
                candidates.push_back(i);
        }
        if (candidates.empty()) { stack.pop_back(); continue; }
        // Rejection sampling avoids modulo bias and is reproducible across standard libraries.
        const auto limit = (std::uint64_t{1} << 32) / candidates.size() * candidates.size();
        std::uint32_t value;
        do { value = rng(); } while (value >= limit);
        int i = candidates[value % candidates.size()];
        map[y + dy[i]][x + dx[i]] = '.';
        x += 2 * dx[i]; y += 2 * dy[i];
        map[y][x] = '.';
        stack.emplace_back(x, y);
    }
    std::vector<std::vector<int>> distance(size, std::vector<int>(size, -1));
    std::queue<std::pair<int, int>> q;
    q.emplace(1, 1); distance[1][1] = 0;
    std::vector<std::pair<int, int>> cells;
    while (!q.empty()) {
        auto [x, y] = q.front(); q.pop();
        if (x != 1 || y != 1) cells.emplace_back(x, y);
        for (int i = 0; i < 4; ++i) {
            int nx = x + dx[i], ny = y + dy[i];
            if (nx >= 0 && ny >= 0 && nx < size && ny < size && map[ny][nx] != '#' && distance[ny][nx] < 0) {
                distance[ny][nx] = distance[y][x] + 1;
                q.emplace(nx, ny);
            }
        }
    }
    // BFS ordering places the exit at maximum distance and crystals along the route range.
    auto [ex, ey] = cells.back(); map[ey][ex] = 'E';
    for (int i = 1; i <= 3; ++i) {
        auto [x, y] = cells[cells.size() * i / 4];
        map[y][x] = 'C';
    }
    map[1][1] = 'S';
    std::ofstream out(argv[1]);
    if (!out) { std::cerr << "Cannot open output file: " << argv[1] << '\n'; return 1; }
    for (const auto& row : map) out << row << '\n';
    out.close();
    if (!out) { std::cerr << "Failed to write map.\n"; return 1; }
    std::cout << "Created " << argv[1] << " (seed " << seed << ")\n";
}
