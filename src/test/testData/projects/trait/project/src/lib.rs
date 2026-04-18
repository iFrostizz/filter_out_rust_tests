mod hi;

pub trait Add {
    fn add(self, other: Self) -> Self;
}

impl Add for u64 {
    fn add(self, other: Self) -> Self {
        self + other
    }
}

#[cfg(test)]
mod tests {
    use super::Add;

    impl Add for i8 {
        fn add(self, other: Self) -> Self {
            self + other
        }
    }
}
