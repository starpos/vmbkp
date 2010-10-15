/**
 * @file
 * @brief Definition and implementation of Generator*.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_GENERATOR_HPP_
#define VMDKBACKUP_GENERATOR_HPP_

/**
 * @brief Virtual class for Generator of T.
 */
template<typename T>
class Generator
{
public:
    virtual ~Generator() {}
    virtual T* operator()() = 0;
};

/**
 * @brief Generator of T with constructor T().
 */
template<typename T>
class Generator0 : public virtual Generator<T>
{
public:
    Generator0() {}
    T* operator()() {
        return new T;
    }
};

/**
 * @brief Generator of T with constructor T(P1);
 */
template<typename T, typename P1>
class Generator1 : public virtual Generator<T>
{
private:
    P1 p1_;
    
public:
    Generator1(P1 p1) : p1_(p1) {}
    T* operator()() {
        return new T(p1_);
    }
};

#endif /* VMDKBACKUP_GENERATOR_HPP_ */
