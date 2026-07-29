package dao;

import models.WordEntry;

public interface WordDao {
    WordEntry findRandomByLevel(int level);
}
